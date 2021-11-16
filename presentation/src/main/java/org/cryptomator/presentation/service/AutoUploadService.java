package org.cryptomator.presentation.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.CloudFile;
import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.CancellationException;
import org.cryptomator.domain.exception.CloudNodeAlreadyExistsException;
import org.cryptomator.domain.exception.FatalBackendException;
import org.cryptomator.domain.exception.MissingCryptorException;
import org.cryptomator.domain.exception.NoSuchCloudFileException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.domain.usecases.ProgressAware;
import org.cryptomator.domain.usecases.cloud.CancelAwareDataSource;
import org.cryptomator.domain.usecases.cloud.DataSource;
import org.cryptomator.domain.usecases.cloud.Flag;
import org.cryptomator.domain.usecases.cloud.UploadFile;
import org.cryptomator.domain.usecases.cloud.UploadState;
import org.cryptomator.presentation.model.AutoUploadFilesStore;
import org.cryptomator.presentation.presenter.UriBasedDataSource;
import org.cryptomator.presentation.util.ContentResolverUtil;
import org.cryptomator.presentation.util.FileUtil;
import org.cryptomator.util.SharedPreferencesHandler;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import timber.log.Timber;

import static java.lang.String.format;
import static org.cryptomator.domain.usecases.cloud.UploadFile.anUploadFile;

public class AutoUploadService extends Service {

	private static final String ACTION_START_AUTO_UPLOAD = "START_AUTO_UPLOAD";
	private static final String ACTION_CANCEL_AUTO_UPLOAD = "CANCEL_AUTO_UPLOAD";
	private static final String ACTION_VAULT_NOT_FOUND = "VAULT_NOT_FOUND";

	private static Cloud cloud;
	private AutoUploadNotification notification;
	private CloudContentRepository cloudContentRepository;
	private ContentResolverUtil contentResolverUtil;
	private FileUtil fileUtil;
	private List<UploadFile> uploadFiles;
	private CloudFolder parent;
	private Context context;
	private long startTimeAutoUploadNotificationDelay;
	private long elapsedTimeAutoUploadNotificationDelay = 0L;
	private Thread worker;
	private volatile boolean cancelled;
	private final Flag cancelledFlag = new Flag() {
		@Override
		public boolean get() {
			return cancelled;
		}
	};

	public static Intent startAutoUploadIntent(Context context, Cloud myCloud) {
		cloud = myCloud;
		Intent startAutoUpload = new Intent(context, AutoUploadService.class);
		startAutoUpload.setAction(ACTION_START_AUTO_UPLOAD);
		return startAutoUpload;
	}

	public static Intent cancelAutoUploadIntent(Context context) {
		Intent cancelAutoUploadIntent = new Intent(context, AutoUploadService.class);
		cancelAutoUploadIntent.setAction(ACTION_CANCEL_AUTO_UPLOAD);
		return cancelAutoUploadIntent;
	}

	public static Intent vaultNotFoundUploadIntent(Context context) {
		Intent cancelAutoUploadIntent = new Intent(context, AutoUploadService.class);
		cancelAutoUploadIntent.setAction(ACTION_VAULT_NOT_FOUND);
		return cancelAutoUploadIntent;
	}

	private void startBackgroundImageUpload(Cloud cloud) {
		try {
			uploadFiles = getUploadFiles(fileUtil.getAutoUploadFilesStore());
		} catch (FatalBackendException e) {
			notification = new AutoUploadNotification(context, 0);
			notification.showGeneralErrorDuringUpload();
			Timber.tag("AutoUploadService").e(e, "Auto upload failed, unable to get images from file store");
			return;
		}

		if (uploadFiles.isEmpty()) {
			return;
		}

		Timber.tag("AutoUploadService").i("Starting background upload");
		notification = new AutoUploadNotification(context, uploadFiles.size());
		notification.show();

		final String autoUploadFolderPath = new SharedPreferencesHandler(context).photoUploadVaultFolder();
		cancelled = false;

		worker = new Thread(() -> {
			try {
				if (autoUploadFolderPath.isEmpty()) {
					parent = cloudContentRepository.root(cloud);
				} else {
					parent = cloudContentRepository.resolve(cloud, autoUploadFolderPath);
				}

				upload(progress -> updateNotification(progress.asPercentage()));
			} catch (FatalBackendException | BackendException | MissingCryptorException e) {
				if (e instanceof NoSuchCloudFileException) {
					notification.showFolderMissing();
				} else if (e instanceof MissingCryptorException) {
					notification.showVaultLockedDuringUpload();
				} else if (e instanceof CancellationException) {
					Timber.tag("AutoUploadService").i("Upload canceled by user");
				} else {
					notification.showGeneralErrorDuringUpload();
				}

				Timber.tag("AutoUploadService").e(e, "Failed to auto upload image(s).");
			}
		});

		worker.start();
	}

	private void updateNotification(int asPercentage) {
		if (elapsedTimeAutoUploadNotificationDelay > 200 && !cancelled) {
			new Handler(Looper.getMainLooper()).post(() -> {
				notification.update(asPercentage);

				startTimeAutoUploadNotificationDelay = System.currentTimeMillis();
				elapsedTimeAutoUploadNotificationDelay = 0;
			});
		} else {
			elapsedTimeAutoUploadNotificationDelay = new Date().getTime() - startTimeAutoUploadNotificationDelay;
		}
	}

	private ArrayList<UploadFile> getUploadFiles(AutoUploadFilesStore autoUploadFilesStore) {
		ArrayList<UploadFile> uploadFiles = new ArrayList<>();

		for (String path : autoUploadFilesStore.getUris()) {
			Uri uri = Uri.fromFile(new File(path));
			String fileName = contentResolverUtil.fileName(uri);
			uploadFiles.add(createUploadFile(fileName, uri));
		}

		return uploadFiles;
	}

	private UploadFile createUploadFile(String fileName, Uri uri) {
		return anUploadFile() //
				.withFileName(fileName) //
				.withDataSource(UriBasedDataSource.from(uri)) //
				.thatIsReplacing(false).build();
	}

	private void upload(ProgressAware<UploadState> progressAware) throws BackendException {
		Set<String> uploadedCloudFileNames = new HashSet<>();

		for (UploadFile file : uploadFiles) {
			try {
				CloudFile uploadedFile = upload(file, progressAware);
				notification.updateFinishedFile();
				uploadedCloudFileNames.add(uploadedFile.getName());
				Timber.tag("AutoUploadService").i("Uploaded file");
				Timber.tag("AutoUploadService").v(format("Uploaded file %s", file.getFileName()));
			} catch (CloudNodeAlreadyExistsException e) {
				Timber.tag("AutoUploadService").i("Not uploading file because it already exists in the cloud");
				Timber.tag("AutoUploadService").v(format("Not uploading file because it already exists in the cloud %s", file.getFileName()));
			} catch (Exception e) {
				cancelled = true;
				fileUtil.removeImagesFromAutoUploads(uploadedCloudFileNames);
				throw e;
			}
		}

		fileUtil.removeImagesFromAutoUploads(uploadedCloudFileNames);
		notification.showUploadFinished(uploadedCloudFileNames.size());
	}

	private CloudFile upload(UploadFile uploadFile, ProgressAware<UploadState> progressAware) throws BackendException {
		try (DataSource dataSource = uploadFile.getDataSource()) {
			return upload(uploadFile, dataSource, progressAware);
		} catch (IOException e) {
			throw new FatalBackendException(e);
		}
	}

	private CloudFile upload(UploadFile uploadFile, DataSource dataSource, ProgressAware<UploadState> progressAware) throws BackendException {
		return writeCloudFile( //
				uploadFile.getFileName(), //
				CancelAwareDataSource.wrap(dataSource, cancelledFlag), //
				uploadFile.getReplacing(), //
				progressAware);
	}

	private CloudFile writeCloudFile(String fileName, CancelAwareDataSource dataSource, boolean replacing, ProgressAware<UploadState> progressAware) throws BackendException {
		Long size = dataSource.size(context);
		CloudFile source = cloudContentRepository.file(parent, fileName, size);
		return cloudContentRepository.write(source, dataSource, progressAware, replacing, size);
	}

	@Override
	public void onCreate() {
		super.onCreate();
		Timber.tag("AutoUploadService").d("created");
		notification = new AutoUploadNotification(this, 0);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Timber.tag("AutoUploadService").i("started");
		if (isStartAutoUpload(intent)) {
			Timber.tag("AutoUploadService").i("Received start upload");

			startBackgroundImageUpload(cloud);
		} else if (isCancelAutoUpload(intent)) {
			Timber.tag("AutoUploadService").i("Received stop auto upload");

			cancelled = true;

			hideNotification();
		} else if(isVaultNotFound(intent)) {
			Timber.tag("AutoUploadService").i("Received show vault not found notification");
			notification.showVaultNotFoundNotification();
		}
		return START_STICKY;
	}

	private boolean isStartAutoUpload(Intent intent) {
		return intent != null //
				&& ACTION_START_AUTO_UPLOAD.equals(intent.getAction());
	}

	private boolean isCancelAutoUpload(Intent intent) {
		return intent != null //
				&& ACTION_CANCEL_AUTO_UPLOAD.equals(intent.getAction());
	}

	private boolean isVaultNotFound(Intent intent) {
		return intent != null //
				&& ACTION_VAULT_NOT_FOUND.equals(intent.getAction());
	}

	@Override
	public void onDestroy() {
		Timber.tag("AutoUploadService").i("onDestroyed");
		if (worker != null) {
			worker.interrupt();
		}
		hideNotification();
	}

	@Override
	public void onTaskRemoved(Intent rootIntent) {
		Timber.tag("AutoUploadService").i("App killed by user");
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

		public void init(CloudContentRepository myCloudContentRepository, FileUtil myFileUtil, ContentResolverUtil myContentResolverUtil, Context myContext) {
			cloudContentRepository = myCloudContentRepository;
			fileUtil = myFileUtil;
			contentResolverUtil = myContentResolverUtil;
			context = myContext;
		}
	}
}
