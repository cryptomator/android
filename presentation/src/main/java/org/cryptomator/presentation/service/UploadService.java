package org.cryptomator.presentation.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;

import org.cryptomator.data.db.UploadCheckpointDao;
import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.CloudFile;
import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.CancellationException;
import org.cryptomator.domain.exception.FatalBackendException;
import org.cryptomator.domain.exception.MissingCryptorException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.domain.usecases.ProgressAware;
import org.cryptomator.domain.usecases.cloud.FileUploadedCallback;
import org.cryptomator.domain.usecases.cloud.FolderCreatedCallback;
import org.cryptomator.domain.usecases.cloud.UploadFile;
import org.cryptomator.domain.usecases.cloud.UploadFiles;
import org.cryptomator.domain.usecases.cloud.UploadFolderFiles;
import org.cryptomator.domain.usecases.cloud.UploadFolderStructure;
import org.cryptomator.domain.usecases.cloud.UploadState;
import org.cryptomator.presentation.CryptomatorApp;
import org.cryptomator.presentation.model.CloudFileModel;
import org.cryptomator.presentation.model.CloudFolderModel;
import org.cryptomator.presentation.util.FileIcon;
import org.cryptomator.presentation.util.FileUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

import timber.log.Timber;

public class UploadService extends Service {

	private static final String ACTION_CANCEL_UPLOAD = "CANCEL_UPLOAD";

	private UploadNotification notification;
	private CloudContentRepository cloudContentRepository;
	private UploadCheckpointDao uploadCheckpointDao;
	private UploadUiUpdates uploadUiUpdates;
	private FileUtil fileUtil;
	private Context appContext;
	private Thread worker;
	private volatile boolean cancelled;
	private volatile Runnable cancelCallback;
	private volatile long startTimeNotificationDelay;
	private volatile long elapsedTimeNotificationDelay = 0L;
	private volatile List<Uri> cleanupUris = Collections.emptyList();
	private volatile KeepAliveLease uploadLease;

	private final Object queueLock = new Object();
	private final Queue<QueuedUpload> pendingUploads = new LinkedList<>();
	private boolean workerRunning = false;

	public static Intent cancelUploadIntent(Context context) {
		Intent cancelIntent = new Intent(context, UploadService.class);
		cancelIntent.setAction(ACTION_CANCEL_UPLOAD);
		return cancelIntent;
	}

	public void startFileUpload(Cloud cloud, String targetFolderPath, List<UploadFile> files,
			Set<String> completedFiles, long vaultId, List<Uri> cleanupUris) {
		startUpload(cloud, targetFolderPath, completedFiles, vaultId, files.size(), cleanupUris, (targetFolder, fileCallback, folderCallback, progressAware) -> {
			UploadFiles uploadFiles = new UploadFiles(appContext, cloudContentRepository, targetFolder, files);
			uploadFiles.setCompletedFiles(completedFiles);
			uploadFiles.setFileUploadedCallback(fileCallback);
			cancelCallback = uploadFiles::onCancel;
			if (cancelled) {
				throw new CancellationException();
			}
			uploadFiles.execute(progressAware);
		});
	}

	public void startFolderUpload(Cloud cloud, String targetFolderPath, UploadFolderStructure folderStructure,
			Set<String> completedFiles, long vaultId) {
		startUpload(cloud, targetFolderPath, completedFiles, vaultId, folderStructure.totalFileCount(), Collections.emptyList(), (targetFolder, fileCallback, folderCallback, progressAware) -> {
			UploadFolderFiles uploadFolderFiles = new UploadFolderFiles(appContext, cloudContentRepository, targetFolder, folderStructure);
			uploadFolderFiles.setCompletedFiles(completedFiles);
			uploadFolderFiles.setFileUploadedCallback(fileCallback);
			uploadFolderFiles.setFolderCreatedCallback(folderCallback);
			cancelCallback = uploadFolderFiles::onCancel;
			if (cancelled) {
				throw new CancellationException();
			}
			uploadFolderFiles.execute(progressAware);
		});
	}

	private void startUpload(Cloud cloud, String targetFolderPath, Set<String> completedFiles,
			long vaultId, int totalFiles, List<Uri> cleanupUris, UploadTask uploadTask) {
		QueuedUpload upload = new QueuedUpload(cloud, targetFolderPath, completedFiles, vaultId, totalFiles, uploadTask, cleanupUris);
		emitVaultActive(vaultId);
		synchronized (queueLock) {
			if (workerRunning) {
				pendingUploads.add(upload);
				Timber.tag("UploadService").i("Upload queued (another in progress)");
				return;
			}
			workerRunning = true;
			cancelled = false;
		}
		startWorker(upload);
	}

	private void startWorker(QueuedUpload initial) {
		KeepAliveLease lease = ((CryptomatorApp) getApplicationContext()).acquireLease(
				LeaseReason.UPLOAD, 5 * 60 * 1000L, 12 * 60 * 60 * 1000L, "upload");
		uploadLease = lease;
		worker = new Thread(() -> {
			QueuedUpload current = initial;
			boolean isFirst = true;
			try {
				while (current != null) {
					if (lease != null) {
						lease.renew();
					}
					if (!processUpload(current, isFirst)) {
						break;
					}
					isFirst = false;
					synchronized (queueLock) {
						current = pendingUploads.poll();
					}
				}
			} finally {
				drainAndCleanupQueue();
				synchronized (queueLock) {
					workerRunning = false;
				}
				if (lease != null) {
					lease.release();
				}
				uploadLease = null;
				stopForeground(STOP_FOREGROUND_DETACH);
				stopSelf();
			}
		});
		worker.start();
	}

	private boolean processUpload(QueuedUpload upload, boolean isFirst) {
		this.cleanupUris = upload.cleanupUris;

		notification = new UploadNotification(appContext, upload.totalFiles);
		if (isFirst) {
			startForeground(UploadNotification.NOTIFICATION_ID, notification.getNotification());
		}
		notification.show();

		FolderKey folderKey = new FolderKey(upload.vaultId, upload.targetFolderPath);

		try {
			if (cancelled) {
				return false;
			}
			CloudFolder targetFolder = upload.targetFolderPath.isEmpty()
					? cloudContentRepository.root(upload.cloud)
					: cloudContentRepository.resolve(upload.cloud, upload.targetFolderPath);

			FileUploadedCallback fileCallback = createCheckpointCallback(upload.vaultId, upload.completedFiles, folderKey);
			FolderCreatedCallback folderCallback = createFolderCreatedCallback(folderKey);
			ProgressAware<UploadState> progressAware = progress -> updateNotification(progress.asPercentage());

			upload.uploadTask.execute(targetFolder, fileCallback, folderCallback, progressAware);

			uploadCheckpointDao.deleteByVaultId(upload.vaultId);
			emitUploadFinished(folderKey);
			emitVaultFinished(upload.vaultId);
			notification.showUploadFinished(upload.totalFiles - upload.completedFiles.size());
			Timber.tag("UploadService").i("Upload completed");
			return true;
		} catch (CancellationException e) {
			clearSnapshot(folderKey);
			uploadCheckpointDao.deleteByVaultId(upload.vaultId);
			emitVaultFinished(upload.vaultId);
			Timber.tag("UploadService").i("Upload canceled by user");
			return false;
		} catch (MissingCryptorException e) {
			clearSnapshot(folderKey);
			emitVaultFinished(upload.vaultId);
			notification.showVaultLockedDuringUpload();
			Timber.tag("UploadService").e(e, "Vault locked during upload");
			return false;
		} catch (BackendException | FatalBackendException e) {
			clearSnapshot(folderKey);
			emitVaultFinished(upload.vaultId);
			notification.showGeneralErrorDuringUpload();
			Timber.tag("UploadService").e(e, "Upload failed");
			return false;
		} finally {
			deleteCleanupUris();
		}
	}

	private void drainAndCleanupQueue() {
		List<QueuedUpload> abandoned;
		synchronized (queueLock) {
			abandoned = new ArrayList<>(pendingUploads);
			pendingUploads.clear();
		}
		for (QueuedUpload upload : abandoned) {
			uploadCheckpointDao.deleteByVaultId(upload.vaultId);
			emitVaultFinished(upload.vaultId);
			deleteTempFiles(upload.cleanupUris);
		}
	}

	private interface UploadTask {
		void execute(CloudFolder targetFolder, FileUploadedCallback fileCallback,
				FolderCreatedCallback folderCallback,
				ProgressAware<UploadState> progressAware) throws BackendException;
	}

	private static class QueuedUpload {
		final Cloud cloud;
		final String targetFolderPath;
		final Set<String> completedFiles;
		final long vaultId;
		final int totalFiles;
		final UploadTask uploadTask;
		final List<Uri> cleanupUris;

		QueuedUpload(Cloud cloud, String targetFolderPath, Set<String> completedFiles,
				long vaultId, int totalFiles, UploadTask uploadTask, List<Uri> cleanupUris) {
			this.cloud = cloud;
			this.targetFolderPath = targetFolderPath;
			this.completedFiles = completedFiles;
			this.vaultId = vaultId;
			this.totalFiles = totalFiles;
			this.uploadTask = uploadTask;
			this.cleanupUris = cleanupUris;
		}
	}

	private FileUploadedCallback createCheckpointCallback(long vaultId, Set<String> completedFiles, FolderKey folderKey) {
		Set<String> uploadedSoFar = new HashSet<>(completedFiles);
		return (relativePath, file) -> {
			uploadedSoFar.add(relativePath);
			try {
				String json = toJsonArray(uploadedSoFar);
				uploadCheckpointDao.updateCompletedFiles(vaultId, json);
			} catch (Exception e) {
				Timber.tag("UploadService").w(e, "Failed to update checkpoint");
			}
			// Only emit for direct children of the target folder.
			// Flat uploads: relativePath is just the filename (no "/").
			// Folder uploads: files inside the folder always have "/" — they're not direct children.
			if (!relativePath.contains("/")) {
				emitFileCreated(folderKey, file);
			}
			new Handler(Looper.getMainLooper()).post(() -> {
				if (notification != null) {
					notification.updateFinishedFile();
				}
			});
		};
	}

	private FolderCreatedCallback createFolderCreatedCallback(FolderKey folderKey) {
		// Only emit for the root folder (direct child of target).
		// The root folder's relativePath has no "/"; subfolders do.
		return (relativePath, folder) -> {
			if (!relativePath.contains("/")) {
				emitFolderCreated(folderKey, folder);
			}
		};
	}

	private void emitFileCreated(FolderKey folderKey, CloudFile file) {
		if (fileUtil != null) {
			CloudFileModel model = new CloudFileModel(file, FileIcon.fileIconFor(file.getName(), fileUtil));
			emitEvent(new UploadUiEvent.NodeCreated(folderKey, model));
		}
	}

	private void emitFolderCreated(FolderKey folderKey, CloudFolder folder) {
		emitEvent(new UploadUiEvent.NodeCreated(folderKey, new CloudFolderModel(folder)));
	}

	private void emitUploadFinished(FolderKey folderKey) {
		emitEvent(new UploadUiEvent.UploadFinished(folderKey));
	}

	private void emitEvent(UploadUiEvent event) {
		safeEmit(() -> uploadUiUpdates.emit(event), "Failed to emit upload event");
	}

	private void clearSnapshot(FolderKey folderKey) {
		if (uploadUiUpdates != null) {
			uploadUiUpdates.clear(folderKey);
		}
	}

	private void emitVaultActive(long vaultId) {
		safeEmit(() -> uploadUiUpdates.markVaultActive(vaultId), "Failed to emit vault active event");
	}

	private void emitVaultFinished(long vaultId) {
		safeEmit(() -> uploadUiUpdates.markVaultFinished(vaultId), "Failed to emit vault finished event");
	}

	private void safeEmit(Runnable action, String errorMessage) {
		if (uploadUiUpdates != null) {
			try {
				action.run();
			} catch (Exception e) {
				Timber.tag("UploadService").w(e, errorMessage);
			}
		}
	}

	private String toJsonArray(Set<String> items) {
		return items.stream()
				.map(item -> "\"" + item.replace("\"", "\\\"") + "\"")
				.collect(Collectors.joining(",", "[", "]"));
	}

	private void deleteCleanupUris() {
		List<Uri> uris = cleanupUris;
		cleanupUris = Collections.emptyList();
		deleteTempFiles(uris);
	}

	private void deleteTempFiles(List<Uri> uris) {
		for (Uri uri : uris) {
			try {
				File file = new File(uri.getPath());
				if (file.delete()) {
					Timber.tag("UploadService").d("Cleaned up temp file: %s", uri);
				}
			} catch (Exception e) {
				Timber.tag("UploadService").w(e, "Failed to clean up temp file");
			}
		}
	}

	private void updateNotification(int asPercentage) {
		KeepAliveLease lease = uploadLease;
		if (lease != null) {
			lease.renew();
		}
		if (elapsedTimeNotificationDelay > 200 && !cancelled) {
			new Handler(Looper.getMainLooper()).post(() -> {
				notification.update(asPercentage);
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
			drainAndCleanupQueue();
			cancelled = true;
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
				UploadCheckpointDao uploadCheckpointDao, UploadUiUpdates uploadUiUpdates,
				FileUtil fileUtil, Context context) {
			UploadService.this.cloudContentRepository = cloudContentRepository;
			UploadService.this.uploadCheckpointDao = uploadCheckpointDao;
			UploadService.this.uploadUiUpdates = uploadUiUpdates;
			UploadService.this.fileUtil = fileUtil;
			UploadService.this.appContext = context;
		}

		public void startFileUpload(Cloud cloud, String targetFolderPath, List<UploadFile> files,
				Set<String> completedFiles, long vaultId, List<Uri> cleanupUris) {
			UploadService.this.startFileUpload(cloud, targetFolderPath, files, completedFiles, vaultId, cleanupUris);
		}

		public void startFolderUpload(Cloud cloud, String targetFolderPath, UploadFolderStructure folderStructure,
				Set<String> completedFiles, long vaultId) {
			UploadService.this.startFolderUpload(cloud, targetFolderPath, folderStructure, completedFiles, vaultId);
		}

		public UploadCheckpointDao getUploadCheckpointDao() {
			return UploadService.this.uploadCheckpointDao;
		}
	}
}
