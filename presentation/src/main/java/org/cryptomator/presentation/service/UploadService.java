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
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import timber.log.Timber;

public class UploadService extends Service {

	private static final String ACTION_CANCEL_UPLOAD = "CANCEL_UPLOAD";
	private static final String EXTRA_CANCEL_VAULT_ID = "cancelVaultId";

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
	private final Set<Long> cancelledVaultIds = ConcurrentHashMap.newKeySet();
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

	public static Intent cancelUploadForVaultIntent(Context context, long vaultId) {
		Intent cancelIntent = new Intent(context, UploadService.class);
		cancelIntent.setAction(ACTION_CANCEL_UPLOAD);
		cancelIntent.putExtra(EXTRA_CANCEL_VAULT_ID, vaultId);
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
			// a cancellation only concerns the uploads that existed when it was requested
			cancelledVaultIds.remove(vaultId);
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
				processUpload(current, isFirst);
				isFirst = false;
				synchronized (queueLock) {
					current = pendingUploads.poll();
					if (current == null) {
						stopWorker(lease);
					}
				}
			}
		});
		worker.start();
	}

	/**
	 * Requires {@link #queueLock}. An upload accepted afterwards then starts a new worker, which this worker's teardown
	 * leaves alone.
	 */
	private void stopWorker(KeepAliveLease lease) {
		workerRunning = false;
		if (lease != null) {
			lease.release();
		}
		uploadLease = null;
		stopForeground(STOP_FOREGROUND_DETACH);
		stopSelf();
	}

	private void processUpload(QueuedUpload upload, boolean isFirst) {
		this.currentVaultId = upload.vaultId;

		UploadNotification uploadNotification = new UploadNotification(appContext, upload.totalFiles);
		notification = uploadNotification;
		if (isFirst) {
			startForeground(UploadNotification.NOTIFICATION_ID, uploadNotification.getNotification());
		}
		uploadNotification.show();

		try {
			throwIfCancelled(upload.vaultId);
			Set<String> completedFiles = uploadCheckpoints.completedFiles(upload.vaultId);
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
			// a cancellation that arrived before the callback was set
			throwIfCancelled(upload.vaultId);
			uploadFolderFiles.execute(progress -> updateNotification(uploadNotification, progress.asPercentage()));

			uploadCheckpoints.delete(upload.vaultId);
			int uploadedCount = upload.totalFiles - completedFiles.size();
			mainHandler.post(() -> uploadNotification.showUploadFinished(uploadedCount));
			Timber.tag("UploadService").i("Upload completed");
		} catch (CancellationException e) {
			uploadCheckpoints.delete(upload.vaultId);
			Timber.tag("UploadService").i("Upload canceled by user");
		} catch (MissingCryptorException e) {
			mainHandler.post(uploadNotification::showVaultLockedDuringUpload);
			Timber.tag("UploadService").e(e, "Vault locked during upload");
		} catch (BackendException | RuntimeException e) {
			mainHandler.post(uploadNotification::showGeneralErrorDuringUpload);
			Timber.tag("UploadService").e(e, "Upload failed");
		} finally {
			cancelCallback = null;
			cancelledVaultIds.remove(upload.vaultId);
			finishVault(upload.vaultId);
		}
	}

	private void throwIfCancelled(long vaultId) {
		if (cancelledVaultIds.contains(vaultId)) {
			throw new CancellationException();
		}
	}

	/**
	 * Requires {@link #queueLock}.
	 */
	private List<QueuedUpload> drainQueue() {
		List<QueuedUpload> drained = new ArrayList<>(pendingUploads);
		pendingUploads.clear();
		return drained;
	}

	private void drainQueueForVault(long vaultId) {
		List<QueuedUpload> removed = new ArrayList<>();
		synchronized (queueLock) {
			Iterator<QueuedUpload> it = pendingUploads.iterator();
			while (it.hasNext()) {
				QueuedUpload upload = it.next();
				if (upload.vaultId == vaultId) {
					removed.add(upload);
					it.remove();
				}
			}
		}
		removed.forEach(this::cleanupAbandonedUpload);
	}

	private void cleanupAbandonedUpload(QueuedUpload upload) {
		uploadCheckpoints.delete(upload.vaultId);
		finishVault(upload.vaultId);
	}

	private void finishVault(long vaultId) {
		synchronized (queueLock) {
			uploadUiUpdates.markVaultFinished(vaultId);
		}
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
			long cancelVaultId = intent.getLongExtra(EXTRA_CANCEL_VAULT_ID, -1L);
			if (cancelVaultId != -1L) {
				Timber.tag("UploadService").i("Received cancel for vault %d", cancelVaultId);
				drainQueueForVault(cancelVaultId);
				cancelledVaultIds.add(cancelVaultId);
				Runnable cancel = cancelCallback;
				long activeVaultId = this.currentVaultId;
				if (cancel != null && activeVaultId == cancelVaultId) {
					cancel.run();
				}
			} else {
				Timber.tag("UploadService").i("Received cancel upload");
				List<QueuedUpload> abandoned;
				synchronized (queueLock) {
					abandoned = drainQueue();
					cancelledVaultIds.addAll(uploadUiUpdates.activeVaultIds());
				}
				abandoned.forEach(this::cleanupAbandonedUpload);
				Runnable cancel = cancelCallback;
				if (cancel != null) {
					cancel.run();
				}
				hideNotification();
				synchronized (queueLock) {
					if (!workerRunning) {
						stopSelf();
					}
				}
			}
		}
		return START_NOT_STICKY;
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
	}
}
