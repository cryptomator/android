package org.cryptomator.presentation.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;

import org.cryptomator.data.db.UploadCheckpointDao;
import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.CancellationException;
import org.cryptomator.domain.exception.FatalBackendException;
import org.cryptomator.domain.exception.MissingCryptorException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.domain.usecases.ProgressAware;
import org.cryptomator.domain.usecases.cloud.FileUploadedCallback;
import org.cryptomator.domain.usecases.cloud.UploadFile;
import org.cryptomator.domain.usecases.cloud.UploadFiles;
import org.cryptomator.domain.usecases.cloud.UploadFolderFiles;
import org.cryptomator.domain.usecases.cloud.UploadFolderStructure;
import org.cryptomator.domain.usecases.cloud.UploadState;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import timber.log.Timber;

public class UploadService extends Service {

	private static final String ACTION_CANCEL_UPLOAD = "CANCEL_UPLOAD";

	private UploadNotification notification;
	private CloudContentRepository cloudContentRepository;
	private UploadCheckpointDao uploadCheckpointDao;
	private Context appContext;
	private Thread worker;
	private volatile boolean cancelled;
	private volatile Runnable cancelCallback;
	private volatile long startTimeNotificationDelay;
	private volatile long elapsedTimeNotificationDelay = 0L;

	public static Intent cancelUploadIntent(Context context) {
		Intent cancelIntent = new Intent(context, UploadService.class);
		cancelIntent.setAction(ACTION_CANCEL_UPLOAD);
		return cancelIntent;
	}

	public void startFileUpload(Cloud cloud, String targetFolderPath, List<UploadFile> files,
			Set<String> completedFiles, long vaultId) {
		startUpload(cloud, targetFolderPath, completedFiles, vaultId, files.size(), (targetFolder, callback, progressAware) -> {
			UploadFiles uploadFiles = new UploadFiles(appContext, cloudContentRepository, targetFolder, files);
			uploadFiles.setCompletedFiles(completedFiles);
			uploadFiles.setFileUploadedCallback(callback);
			cancelCallback = uploadFiles::onCancel;
			if (cancelled) {
				throw new CancellationException();
			}
			uploadFiles.execute(progressAware);
		});
	}

	public void startFolderUpload(Cloud cloud, String targetFolderPath, UploadFolderStructure folderStructure,
			Set<String> completedFiles, long vaultId) {
		startUpload(cloud, targetFolderPath, completedFiles, vaultId, folderStructure.totalFileCount(), (targetFolder, callback, progressAware) -> {
			UploadFolderFiles uploadFolderFiles = new UploadFolderFiles(appContext, cloudContentRepository, targetFolder, folderStructure);
			uploadFolderFiles.setCompletedFiles(completedFiles);
			uploadFolderFiles.setFileUploadedCallback(callback);
			cancelCallback = uploadFolderFiles::onCancel;
			if (cancelled) {
				throw new CancellationException();
			}
			uploadFolderFiles.execute(progressAware);
		});
	}

	private void startUpload(Cloud cloud, String targetFolderPath, Set<String> completedFiles,
			long vaultId, int totalFiles, UploadTask uploadTask) {
		if (worker != null && worker.isAlive()) {
			Timber.tag("UploadService").w("Upload already in progress, ignoring request");
			return;
		}

		notification = new UploadNotification(appContext, totalFiles);

		startForeground(UploadNotification.NOTIFICATION_ID, notification.getNotification());
		notification.show();

		cancelled = false;

		worker = new Thread(() -> {
			try {
				CloudFolder targetFolder = targetFolderPath.isEmpty()
						? cloudContentRepository.root(cloud)
						: cloudContentRepository.resolve(cloud, targetFolderPath);

				FileUploadedCallback callback = createCheckpointCallback(vaultId);
				ProgressAware<UploadState> progressAware = progress -> updateNotification(progress.asPercentage());

				uploadTask.execute(targetFolder, callback, progressAware);

				uploadCheckpointDao.deleteByVaultId(vaultId);
				notification.showUploadFinished(totalFiles - completedFiles.size());
				Timber.tag("UploadService").i("Upload completed");
			} catch (CancellationException e) {
				Timber.tag("UploadService").i("Upload canceled by user");
			} catch (MissingCryptorException e) {
				notification.showVaultLockedDuringUpload();
				Timber.tag("UploadService").e(e, "Vault locked during upload");
			} catch (BackendException | FatalBackendException e) {
				notification.showGeneralErrorDuringUpload();
				Timber.tag("UploadService").e(e, "Upload failed");
			} finally {
				stopForeground(STOP_FOREGROUND_DETACH);
				stopSelf();
			}
		});

		worker.start();
	}

	@FunctionalInterface
	private interface UploadTask {
		void execute(CloudFolder targetFolder, FileUploadedCallback callback,
				ProgressAware<UploadState> progressAware) throws BackendException;
	}

	private FileUploadedCallback createCheckpointCallback(long vaultId) {
		Set<String> uploadedSoFar = new HashSet<>();
		return relativePath -> {
			uploadedSoFar.add(relativePath);
			try {
				String json = toJsonArray(uploadedSoFar);
				uploadCheckpointDao.updateCompletedFiles(vaultId, json);
			} catch (Exception e) {
				Timber.tag("UploadService").w(e, "Failed to update checkpoint");
			}
			new Handler(Looper.getMainLooper()).post(() -> {
				if (notification != null) {
					notification.updateFinishedFile();
				}
			});
		};
	}

	private String toJsonArray(Set<String> items) {
		return items.stream()
				.map(item -> "\"" + item.replace("\"", "\\\"") + "\"")
				.collect(Collectors.joining(",", "[", "]"));
	}

	private void updateNotification(int asPercentage) {
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
			cancelled = true;
			Runnable cancel = cancelCallback;
			if (cancel != null) {
				cancel.run();
			}
			hideNotification();
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
				UploadCheckpointDao uploadCheckpointDao, Context context) {
			UploadService.this.cloudContentRepository = cloudContentRepository;
			UploadService.this.uploadCheckpointDao = uploadCheckpointDao;
			UploadService.this.appContext = context;
		}

		public void startFileUpload(Cloud cloud, String targetFolderPath, List<UploadFile> files,
				Set<String> completedFiles, long vaultId) {
			UploadService.this.startFileUpload(cloud, targetFolderPath, files, completedFiles, vaultId);
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
