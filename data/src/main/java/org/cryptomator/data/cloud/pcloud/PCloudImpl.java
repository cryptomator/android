package org.cryptomator.data.cloud.pcloud;

import android.content.Context;

import com.pcloud.sdk.ApiClient;
import com.pcloud.sdk.ApiError;
import com.pcloud.sdk.DataSink;
import com.pcloud.sdk.DownloadOptions;
import com.pcloud.sdk.FileLink;
import com.pcloud.sdk.ProgressListener;
import com.pcloud.sdk.RemoteEntry;
import com.pcloud.sdk.RemoteFile;
import com.pcloud.sdk.RemoteFolder;
import com.pcloud.sdk.UploadOptions;
import com.pcloud.sdk.UserInfo;
import com.tomclaw.cache.DiskLruCache;

import org.cryptomator.data.util.CopyStream;
import org.cryptomator.domain.PCloud;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.CloudNodeAlreadyExistsException;
import org.cryptomator.domain.exception.FatalBackendException;
import org.cryptomator.domain.exception.ForbiddenException;
import org.cryptomator.domain.exception.NetworkConnectionException;
import org.cryptomator.domain.exception.NoSuchCloudFileException;
import org.cryptomator.domain.exception.UnauthorizedException;
import org.cryptomator.domain.exception.authentication.NoAuthenticationProvidedException;
import org.cryptomator.domain.exception.authentication.WrongCredentialsException;
import org.cryptomator.domain.usecases.ProgressAware;
import org.cryptomator.domain.usecases.cloud.DataSource;
import org.cryptomator.domain.usecases.cloud.DownloadState;
import org.cryptomator.domain.usecases.cloud.Progress;
import org.cryptomator.domain.usecases.cloud.UploadState;
import org.cryptomator.util.Optional;
import org.cryptomator.util.SharedPreferencesHandler;
import org.cryptomator.util.file.LruFileCacheUtil;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;
import okio.Source;
import timber.log.Timber;

import static org.cryptomator.domain.usecases.cloud.Progress.progress;
import static org.cryptomator.util.file.LruFileCacheUtil.Cache.PCLOUD;
import static org.cryptomator.util.file.LruFileCacheUtil.retrieveFromLruCache;
import static org.cryptomator.util.file.LruFileCacheUtil.storeToLruCache;

class PCloudImpl {

	private final PCloudClientFactory clientFactory = new PCloudClientFactory();
	private final PCloud cloud;
	private final RootPCloudFolder root;
	private final Context context;

	private final SharedPreferencesHandler sharedPreferencesHandler;
	private DiskLruCache diskLruCache;

	PCloudImpl(Context context, PCloud cloud) {
		if (cloud.accessToken() == null) {
			throw new NoAuthenticationProvidedException(cloud);
		}

		this.context = context;
		this.cloud = cloud;
		this.root = new RootPCloudFolder(cloud);
		this.sharedPreferencesHandler = new SharedPreferencesHandler(context);
	}

	private ApiClient client() {
		return clientFactory.getClient(cloud.accessToken(), cloud.url(), context);
	}

	public PCloudFolder root() {
		return root;
	}

	public PCloudFolder resolve(String path) throws IOException, BackendException {
		if (path.startsWith("/")) {
			path = path.substring(1);
		}
		String[] names = path.split("/");
		PCloudFolder folder = root;
		for (String name : names) {
			folder = folder(folder, name);
		}
		return folder;
	}

	public PCloudFile file(PCloudFolder parent, String name) throws BackendException, IOException {
		return file(parent, name, Optional.empty());
	}

	public PCloudFile file(PCloudFolder parent, String name, Optional<Long> size) throws BackendException, IOException {
		return PCloudNodeFactory.file(parent, name, size, parent.getPath() + "/" + name);
	}

	public PCloudFolder folder(PCloudFolder parent, String name) throws IOException, BackendException {
		return PCloudNodeFactory.folder(parent, name, parent.getPath() + "/" + name);
	}

	public boolean exists(PCloudNode node) throws IOException, BackendException {
		try {
			if (node instanceof PCloudFolder) {
				client().loadFolder(node.getPath()).execute();
			} else {
				client().loadFile(node.getPath()).execute();
			}
			return true;
		} catch (ApiError ex) {
			Set<Integer> ignoredErrorCodes = new HashSet<>();
			ignoredErrorCodes.add(PCloudApiError.PCloudApiErrorCodes.DIRECTORY_DOES_NOT_EXIST.getValue());
			ignoredErrorCodes.add(PCloudApiError.PCloudApiErrorCodes.COMPONENT_OF_PARENT_DIRECTORY_DOES_NOT_EXIST.getValue());
			ignoredErrorCodes.add(PCloudApiError.PCloudApiErrorCodes.INVALID_FILE_OR_FOLDER_NAME.getValue());
			ignoredErrorCodes.add(PCloudApiError.PCloudApiErrorCodes.FILE_OR_FOLDER_NOT_FOUND.getValue());
			handleApiError(ex, ignoredErrorCodes);
			return false;
		}
	}

	public List<PCloudNode> list(PCloudFolder folder) throws IOException, BackendException {
		List<PCloudNode> result = new ArrayList<>();

		try {
			RemoteFolder listFolderResult = client().listFolder(folder.getPath()).execute();
			List<RemoteEntry> entryMetadata = listFolderResult.children();
			for (RemoteEntry metadata : entryMetadata) {
				result.add(PCloudNodeFactory.from(folder, metadata));
			}
			return result;
		} catch(ApiError ex) {
			handleApiError(ex);
			throw new FatalBackendException(ex);
		}
	}

	public PCloudFolder create(PCloudFolder folder) throws IOException, BackendException  {
		if (!exists(folder.getParent())) {
			folder = new PCloudFolder( //
					create(folder.getParent()), //
					folder.getName(), folder.getPath() //
			);
		}

		try {
			RemoteFolder createdFolder = client() //
					.createFolder(folder.getPath()) //
					.execute();
			return PCloudNodeFactory.folder(folder.getParent(), createdFolder);
		} catch (ApiError ex) {
			handleApiError(ex);
			throw new FatalBackendException(ex);
		}
	}

	public PCloudNode move(PCloudNode source, PCloudNode target) throws IOException, BackendException {
		if (exists(target)) {
			throw new CloudNodeAlreadyExistsException(target.getName());
		}

		try {
			if (source instanceof PCloudFolder) {
				return PCloudNodeFactory.from(target.getParent(), client().moveFolder(source.getPath(), target.getPath()).execute());
			} else {
				return PCloudNodeFactory.from(target.getParent(), client().moveFile(source.getPath(), target.getPath()).execute());
			}
		} catch(ApiError ex) {
			handleApiError(ex);
			throw new FatalBackendException(ex);
		}
	}

	public PCloudFile write(PCloudFile file, DataSource data, final ProgressAware<UploadState> progressAware, boolean replace, long size)
			throws IOException, BackendException {
		if (!replace && exists(file)) {
			throw new CloudNodeAlreadyExistsException("CloudNode already exists and replace is false");
		}

		progressAware.onProgress(Progress.started(UploadState.upload(file)));
		UploadOptions uploadOptions = UploadOptions.DEFAULT;
		if (replace) {
			uploadOptions = UploadOptions.OVERRIDE_FILE;
		}

		RemoteFile uploadedFile = uploadFile(file, data, progressAware, uploadOptions, size);

		progressAware.onProgress(Progress.completed(UploadState.upload(file)));

		return PCloudNodeFactory.file(file.getParent(), uploadedFile);

	}

	private RemoteFile uploadFile(final PCloudFile file, DataSource data, final ProgressAware<UploadState> progressAware, UploadOptions uploadOptions, final long size) //
			throws IOException, BackendException {
			ProgressListener listener = (done, total) -> progressAware.onProgress( //
					progress(UploadState.upload(file)) //
							.between(0) //
							.and(size) //
							.withValue(done));

		com.pcloud.sdk.DataSource pCloudDataSource = new com.pcloud.sdk.DataSource() {
			@Override
			public long contentLength() {
				return data.size(context).get();
			}

			@Override
			public void writeTo(BufferedSink sink) throws IOException {
				try (Source source = Okio.source(data.open(context))) {
					sink.writeAll(source);
				}
			}
		};

		try {
			return client() //
					.createFile(file.getParent().getPath(), file.getName(), pCloudDataSource, new Date(), listener, uploadOptions) //
					.execute();
		} catch (ApiError ex) {
			handleApiError(ex);
			throw new FatalBackendException(ex);
		}
	}

	public void read(PCloudFile file, Optional<File> encryptedTmpFile, OutputStream data, final ProgressAware<DownloadState> progressAware) throws IOException, BackendException {
		progressAware.onProgress(Progress.started(DownloadState.download(file)));

		Optional<String> cacheKey = Optional.empty();
		Optional<File> cacheFile = Optional.empty();

		RemoteFile remoteFile;

		if (sharedPreferencesHandler.useLruCache() && createLruCache(sharedPreferencesHandler.lruCacheSize())) {
			try {
				remoteFile = client().loadFile(file.getPath()).execute().asFile();
				cacheKey = Optional.of(remoteFile.fileId() + remoteFile.hash());
			} catch(ApiError ex) {
				handleApiError(ex);
			}

			File cachedFile = diskLruCache.get(cacheKey.get());
			cacheFile = cachedFile != null ? Optional.of(cachedFile) : Optional.empty();
		}

		if (sharedPreferencesHandler.useLruCache() && cacheFile.isPresent()) {
			try {
				retrieveFromLruCache(cacheFile.get(), data);
			} catch (IOException e) {
				Timber.tag("PCloudImpl").w(e, "Error while retrieving content from Cache, get from web request");
				writeToData(file, data, encryptedTmpFile, cacheKey, progressAware);
			}
		} else {
			writeToData(file, data, encryptedTmpFile, cacheKey, progressAware);
		}

		progressAware.onProgress(Progress.completed(DownloadState.download(file)));
	}

	private void writeToData(final PCloudFile file, //
			final OutputStream data, //
			final Optional<File> encryptedTmpFile, //
			final Optional<String> cacheKey, //
			final ProgressAware<DownloadState> progressAware) throws IOException, BackendException {
		try {
			FileLink fileLink = client().createFileLink(file.getPath(), DownloadOptions.DEFAULT).execute();

			ProgressListener listener = (done, total) -> progressAware.onProgress( //
					progress(DownloadState.download(file)) //
							.between(0) //
							.and(file.getSize().orElse(Long.MAX_VALUE)) //
							.withValue(done));

			DataSink sink = new DataSink() {
				@Override
				public void readAll(BufferedSource source) {
					CopyStream.copyStreamToStream(source.inputStream(), data);
				}
			};

			client().download(fileLink, sink, listener).execute();
		} catch(ApiError ex) {
			handleApiError(ex);
		}

		if (sharedPreferencesHandler.useLruCache() && encryptedTmpFile.isPresent() && cacheKey.isPresent()) {
			try {
				storeToLruCache(diskLruCache, cacheKey.get(), encryptedTmpFile.get());
			} catch (IOException e) {
				Timber.tag("PCloudImpl").e(e, "Failed to write downloaded file in LRU cache");
			}
		}

	}

	public void delete(PCloudNode node) throws IOException, BackendException {
		try {
			if (node instanceof PCloudFolder) {
				client() //
						.deleteFolder(node.getPath(), true).execute();
			} else {
				client() //
						.deleteFile(node.getPath()).execute();
			}
		} catch(ApiError ex) {
			handleApiError(ex);
		}
	}

	public String currentAccount() throws IOException, BackendException {
		try {
			UserInfo currentAccount = client() //
					.getUserInfo() //
					.execute();
			return currentAccount.email();
		} catch(ApiError ex) {
			handleApiError(ex);
			throw new FatalBackendException(ex);
		}
	}

	private boolean createLruCache(int cacheSize) {
		if (diskLruCache == null) {
			try {
				diskLruCache = DiskLruCache.create(new LruFileCacheUtil(context).resolve(PCLOUD), cacheSize);
			} catch (IOException e) {
				Timber.tag("PCloudImpl").e(e, "Failed to setup LRU cache");
				return false;
			}
		}

		return true;
	}

	private void handleApiError(ApiError ex) throws BackendException {
		handleApiError(ex, null);
	}

	private void handleApiError(ApiError ex, Set<Integer> errorCodes) throws BackendException {
		handleApiError(ex, errorCodes, null);
	}

	private void handleApiError(ApiError ex, Set<Integer> errorCodes, String name) throws BackendException {
		if (errorCodes == null  || !errorCodes.contains(ex.errorCode())) {
			int errorCode = ex.errorCode();
			if (PCloudApiError.isCloudNodeAlreadyExistsException(errorCode)) {
				throw new CloudNodeAlreadyExistsException(name);
			} else if (PCloudApiError.isForbiddenException(errorCode)){
				throw new ForbiddenException();
			} else if (PCloudApiError.isNetworkConnectionException(errorCode)) {
				throw new NetworkConnectionException(ex);
			} else if (PCloudApiError.isNoSuchCloudFileException(errorCode)) {
				throw new NoSuchCloudFileException(name);
			} else if (PCloudApiError.isWrongCredentialsException(errorCode)) {
				throw new WrongCredentialsException(cloud);
			} else if (PCloudApiError.isUnauthorizedException(errorCode)) {
				throw new UnauthorizedException();
			} else {
				throw new FatalBackendException(ex);
			}
		}
	}
}
