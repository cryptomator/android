package org.cryptomator.presentation.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;

import org.cryptomator.data.db.entities.UploadCheckpointEntity;
import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.CancellationException;
import org.cryptomator.domain.exception.MissingCryptorException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.domain.repository.VaultDocumentRepository;
import org.cryptomator.domain.repository.VaultRepository;
import org.cryptomator.domain.usecases.cloud.UploadFolderFiles;
import org.cryptomator.domain.usecases.cloud.UploadFolderStructure;
import org.cryptomator.presentation.CryptomatorApp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import timber.log.Timber;

public class UploadService extends Service {

	private static final String ACTION_CANCEL_UPLOAD = "CANCEL_UPLOAD";

	private final Handler mainHandler = new Handler(Looper.getMainLooper());
	private UploadNotification notification;
	private CloudContentRepository cloudContentRepository;
	private VaultDocumentRepository vaultDocumentRepository;
	private VaultRepository vaultRepository;
	private UploadCheckpoints uploadCheckpoints;
	private UploadUiUpdates uploadUiUpdates;
	private Context appContext;
	private Thread worker;
	private volatile Runnable cancelCallback;
	private volatile long currentVaultId = -1L;
	private final Map<Long, StopReason> stopRequests = new ConcurrentHashMap<>();
	private volatile long startTimeNotificationDelay;
	private volatile long elapsedTimeNotificationDelay = 0L;
	private volatile KeepAliveLease uploadLease;

	/**
	 * Guards the queue, the worker's state and the vaults {@link UploadUiUpdates} tracks as active, so that each vault has
	 * at most one upload queued or running.
	 */
	private final Object queueLock = new Object();
	private final Queue<QueuedUpload> pendingUploads = new LinkedList<>();
	private boolean workerRunning = false;

	public static Intent cancelUploadIntent(Context context) {
		Intent cancelIntent = new Intent(context, UploadService.class);
		cancelIntent.setAction(ACTION_CANCEL_UPLOAD);
		return cancelIntent;
	}

	public UploadAdmission startFolderUpload(Cloud cloud, UploadFolderStructure folderStructure, long vaultId, String targetFolderPath, String sourceFolderUri, boolean replacing) {
		UploadCheckpointEntity checkpoint = new UploadCheckpointEntity();
		checkpoint.setVaultId(vaultId);
		checkpoint.setTargetFolderPath(targetFolderPath);
		checkpoint.setSourceFolderUri(sourceFolderUri);
		checkpoint.setSourceFolderName(folderStructure.getFolderName());
		checkpoint.setTotalFileCount(folderStructure.totalFileCount());
		checkpoint.setReplacing(replacing);
		return admit(cloud, folderStructure, checkpoint, false);
	}

	public UploadAdmission resumeFolderUpload(Cloud cloud, UploadFolderStructure folderStructure, UploadCheckpointEntity checkpoint) {
		return admit(cloud, folderStructure, checkpoint, true);
	}

	/**
	 * @param resume whether {@code checkpoint} is the stored one of an interrupted upload, otherwise it is stored unless the
	 * vault has a checkpoint already
	 */
	private UploadAdmission admit(Cloud cloud, UploadFolderStructure folderStructure, UploadCheckpointEntity checkpoint, boolean resume) {
		long vaultId = checkpoint.getVaultId();
		synchronized (queueLock) {
			if (uploadUiUpdates.isVaultActive(vaultId) || (!resume && !uploadCheckpoints.createIfAbsent(checkpoint))) {
				return UploadAdmission.BUSY;
			}
			// a stop only concerns the uploads that existed when it was requested
			stopRequests.remove(vaultId);
			uploadUiUpdates.markVaultActive(vaultId);
			QueuedUpload upload = new QueuedUpload(cloud, checkpoint, folderStructure);
			if (workerRunning) {
				pendingUploads.add(upload);
				Timber.tag("UploadService").i("Upload queued (another in progress)");
			} else {
				workerRunning = true;
				startWorker(upload);
			}
		}
		return UploadAdmission.ACCEPTED;
	}

	private void startWorker(QueuedUpload initial) {
		KeepAliveLease lease = ((CryptomatorApp) getApplicationContext()).acquireLease(
				LeaseReason.UPLOAD, 5 * 60 * 1000L, 12 * 60 * 60 * 1000L, "upload");
		uploadLease = lease;
		worker = new Thread(() -> {
			QueuedUpload current = initial;
			boolean isFirst = true;
			while (current != null) {
				if (lease != null) {
					lease.renew();
				}
				boolean canceled = processUpload(current, isFirst);
				isFirst = false;
				synchronized (queueLock) {
					current = pendingUploads.poll();
					if (current == null) {
						stopWorker(lease, canceled);
					}
				}
			}
		});
		worker.start();
	}

	/**
	 * Requires {@link #queueLock}. An upload accepted afterwards then starts a new worker, which this worker's teardown
	 * leaves alone.
	 *
	 * @param lastCanceled whether the user canceled the last upload, whose notification is then removed instead of left
	 */
	private void stopWorker(KeepAliveLease lease, boolean lastCanceled) {
		workerRunning = false;
		if (lease != null) {
			lease.release();
		}
		uploadLease = null;
		stopForeground(lastCanceled ? STOP_FOREGROUND_REMOVE : STOP_FOREGROUND_DETACH);
		stopSelf();
	}

	/**
	 * Posts the notification changes of the upload to the main thread in the order they are made, so that those of an
	 * upload that has ended don't overwrite the notification of the next one. Only {@code startForeground} shows the first
	 * notification of a worker right away.
	 *
	 * @return whether the user canceled the upload
	 */
	private boolean processUpload(QueuedUpload upload, boolean isFirst) {
		this.currentVaultId = upload.vaultId;

		UploadNotification uploadNotification = new UploadNotification(appContext, upload.totalFiles);
		notification = uploadNotification;
		try {
			if (isFirst) {
				startForeground(UploadNotification.NOTIFICATION_ID, uploadNotification.getNotification());
			}
			throwIfStopped(upload.vaultId);
			Set<String> completedFiles = uploadCheckpoints.completedFiles(upload.vaultId);
			uploadNotification.setCompletedFiles(completedFiles.size());
			mainHandler.post(uploadNotification::show);

			CloudFolder targetFolder = upload.targetFolderPath.isEmpty()
					? cloudContentRepository.root(upload.cloud)
					: cloudContentRepository.resolve(upload.cloud, upload.targetFolderPath);

			UploadFolderFiles uploadFolderFiles = new UploadFolderFiles(appContext, cloudContentRepository, vaultDocumentRepository, vaultRepository, targetFolder, upload.folderStructure);
			uploadFolderFiles.setCompletedFiles(completedFiles);
			uploadFolderFiles.setFileUploadedCallback(relativePath -> {
				uploadCheckpoints.recordCompleted(upload.vaultId, relativePath);
				mainHandler.post(uploadNotification::updateFinishedFile);
			});
			cancelCallback = uploadFolderFiles::onCancel;
			// a stop that was requested before the callback was set
			throwIfStopped(upload.vaultId);
			int uploadedCount = uploadFolderFiles.execute(progress -> updateNotification(uploadNotification, progress.asPercentage())).size();

			uploadCheckpoints.delete(upload.vaultId);
			mainHandler.post(() -> uploadNotification.showUploadFinished(uploadedCount));
			Timber.tag("UploadService").i("Upload completed");
		} catch (BackendException | RuntimeException e) {
			// a requested stop decides the outcome, also when a lock of the vault right after it is what ended the upload
			StopReason stopReason = stopRequests.get(upload.vaultId);
			if (stopReason == StopReason.CANCELED) {
				uploadCheckpoints.delete(upload.vaultId);
				mainHandler.post(uploadNotification::hide);
				Timber.tag("UploadService").i("Upload canceled by user");
				return true;
			} else if (stopReason == StopReason.INTERRUPTED) {
				mainHandler.post(uploadNotification::showUploadInterrupted);
				Timber.tag("UploadService").i("Upload interrupted by the foreground service timeout");
			} else if (e instanceof MissingCryptorException) {
				mainHandler.post(uploadNotification::showVaultLockedDuringUpload);
				Timber.tag("UploadService").e(e, "Vault locked during upload");
			} else {
				mainHandler.post(uploadNotification::showGeneralErrorDuringUpload);
				Timber.tag("UploadService").e(e, "Upload failed");
			}
		} finally {
			cancelCallback = null;
			stopRequests.remove(upload.vaultId);
			finishVault(upload.vaultId);
		}
		return false;
	}

	private void throwIfStopped(long vaultId) {
		if (stopRequests.containsKey(vaultId)) {
			throw new CancellationException();
		}
	}

	/**
	 * Stops the upload of the vault with {@code vaultId}, or those of all vaults if it is {@code null}. The running upload
	 * ends at its next step, and queued ones don't start. A canceled upload's checkpoint is deleted, an interrupted one's
	 * is kept to be resumed.
	 */
	private void stopUploads(@Nullable Long vaultId, StopReason reason) {
		Set<Long> vaultIds;
		List<QueuedUpload> dequeued = new ArrayList<>();
		synchronized (queueLock) {
			vaultIds = vaultId != null ? Collections.singleton(vaultId) : uploadUiUpdates.activeVaultIds();
			for (Long id : vaultIds) {
				stopRequests.put(id, reason);
			}
			Iterator<QueuedUpload> it = pendingUploads.iterator();
			while (it.hasNext()) {
				QueuedUpload upload = it.next();
				if (vaultIds.contains(upload.vaultId)) {
					dequeued.add(upload);
					it.remove();
				}
			}
		}
		for (QueuedUpload upload : dequeued) {
			if (reason == StopReason.CANCELED) {
				uploadCheckpoints.delete(upload.vaultId);
			}
			stopRequests.remove(upload.vaultId);
			finishVault(upload.vaultId);
		}
		Runnable cancel = cancelCallback;
		if (cancel != null && vaultIds.contains(currentVaultId)) {
			cancel.run();
		}
	}

	private void finishVault(long vaultId) {
		synchronized (queueLock) {
			uploadUiUpdates.markVaultFinished(vaultId);
		}
	}

	private enum StopReason {
		CANCELED, INTERRUPTED
	}

	private static class QueuedUpload {
		final Cloud cloud;
		final String targetFolderPath;
		final UploadFolderStructure folderStructure;
		final long vaultId;
		final int totalFiles;

		QueuedUpload(Cloud cloud, UploadCheckpointEntity checkpoint, UploadFolderStructure folderStructure) {
			this.cloud = cloud;
			this.targetFolderPath = checkpoint.getTargetFolderPath();
			this.folderStructure = folderStructure;
			this.vaultId = checkpoint.getVaultId();
			this.totalFiles = checkpoint.getTotalFileCount();
		}
	}

	private void updateNotification(UploadNotification uploadNotification, int asPercentage) {
		KeepAliveLease lease = uploadLease;
		if (lease != null) {
			lease.renew();
		}
		if (elapsedTimeNotificationDelay > 200) {
			mainHandler.post(() -> {
				uploadNotification.update(asPercentage);
				startTimeNotificationDelay = System.currentTimeMillis();
				elapsedTimeNotificationDelay = 0;
			});
		} else {
			elapsedTimeNotificationDelay = System.currentTimeMillis() - startTimeNotificationDelay;
		}
	}

	@Override
	public void onCreate() {
		super.onCreate();
		Timber.tag("UploadService").d("created");
		notification = new UploadNotification(this, 0);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Timber.tag("UploadService").i("started");
		if (isCancelUpload(intent)) {
			Timber.tag("UploadService").i("Received cancel upload");
			stopUploads(null, StopReason.CANCELED);
			hideNotification();
			synchronized (queueLock) {
				if (!workerRunning) {
					stopSelf();
				}
			}
		}
		return START_NOT_STICKY;
	}

	/**
	 * Called once the uploads have used up the time Android 15 grants foreground services of type dataSync. Stops them but
	 * keeps their checkpoints, so that they can be resumed.
	 */
	@Override
	public void onTimeout(int startId, int fgsType) {
		Timber.tag("UploadService").i("Foreground service timed out");
		stopUploads(null, StopReason.INTERRUPTED);
		stopForeground(STOP_FOREGROUND_DETACH);
		stopSelf();
	}

	private boolean isCancelUpload(Intent intent) {
		return intent != null && ACTION_CANCEL_UPLOAD.equals(intent.getAction());
	}

	@Override
	public void onDestroy() {
		Timber.tag("UploadService").i("onDestroyed");
		if (worker != null) {
			worker.interrupt();
		}
		hideNotification();
	}

	@Override
	public void onTaskRemoved(Intent rootIntent) {
		Timber.tag("UploadService").i("App killed by user");
	}

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		return new Binder();
	}

	private void hideNotification() {
		if (notification != null) {
			notification.hide();
		}
	}

	public class Binder extends android.os.Binder {

		Binder() {
		}

		public void init(CloudContentRepository cloudContentRepository,
				VaultDocumentRepository vaultDocumentRepository,
				VaultRepository vaultRepository,
				UploadCheckpoints uploadCheckpoints, UploadUiUpdates uploadUiUpdates,
				Context context) {
			UploadService.this.cloudContentRepository = cloudContentRepository;
			UploadService.this.vaultDocumentRepository = vaultDocumentRepository;
			UploadService.this.vaultRepository = vaultRepository;
			UploadService.this.uploadCheckpoints = uploadCheckpoints;
			UploadService.this.uploadUiUpdates = uploadUiUpdates;
			UploadService.this.appContext = context;
		}

		public UploadAdmission startFolderUpload(Cloud cloud, UploadFolderStructure folderStructure, long vaultId, String targetFolderPath, String sourceFolderUri, boolean replacing) {
			return UploadService.this.startFolderUpload(cloud, folderStructure, vaultId, targetFolderPath, sourceFolderUri, replacing);
		}

		public UploadAdmission resumeFolderUpload(Cloud cloud, UploadFolderStructure folderStructure, UploadCheckpointEntity checkpoint) {
			return UploadService.this.resumeFolderUpload(cloud, folderStructure, checkpoint);
		}

		/**
		 * Marks the vault's upload as canceled before returning, so that a lock of the vault right afterwards doesn't leave
		 * it to be resumed.
		 */
		public void cancelFolderUpload(long vaultId) {
			UploadService.this.stopUploads(vaultId, StopReason.CANCELED);
		}
	}
}
