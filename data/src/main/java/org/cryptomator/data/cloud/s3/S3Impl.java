package org.cryptomator.data.cloud.s3;

import android.content.Context;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AbstractPutObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.S3ObjectSummary;
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
import org.cryptomator.domain.S3Cloud;
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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
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

class S3Impl {

	private static final String SUFFIX = "/";

	private final S3ClientFactory clientFactory = new S3ClientFactory();
	private final S3Cloud cloud;
	private final RootS3Folder root;
	private final Context context;

	private final SharedPreferencesHandler sharedPreferencesHandler;
	private DiskLruCache diskLruCache;

	S3Impl(Context context, S3Cloud cloud) {
		if (cloud.accessKey() == null || cloud.secretKey() == null) {
			throw new NoAuthenticationProvidedException(cloud);
		}

		this.context = context;
		this.cloud = cloud;
		this.root = new RootS3Folder(cloud);
		this.sharedPreferencesHandler = new SharedPreferencesHandler(context);
	}

	private AmazonS3 client() {
		return clientFactory.getClient(cloud, context);
	}

	public S3Folder root() {
		return root;
	}

	public S3Folder resolve(String path) throws IOException, BackendException {
		if (path.startsWith("/")) {
			path = path.substring(1);
		}
		String[] names = path.split("/");
		S3Folder folder = root;
		for (String name : names) {
			folder = folder(folder, name);
		}
		return folder;
	}

	public S3File file(S3Folder parent, String name) throws BackendException, IOException {
		return file(parent, name, Optional.empty());
	}

	public S3File file(S3Folder parent, String name, Optional<Long> size) throws BackendException, IOException {
		return S3CloudNodeFactory.file(parent, name, size, parent.getPath() + "/" + name);
	}

	public S3Folder folder(S3Folder parent, String name) throws IOException, BackendException {
		return S3CloudNodeFactory.folder(parent, name, parent.getPath() + "/" + name);
	}

	public boolean exists(S3Node node) throws IOException, BackendException {
		try {
			if (node instanceof S3Folder) {
				client().loadFolder(node.getPath()).execute();
			} else {
				client().loadFile(node.getPath()).execute();
			}
			return true;
		} catch (ApiError ex) {
			handleApiError(ex, PCloudApiError.ignoreExistsSet, node.getName());
			return false;
		}
	}

	public List<S3Node> list(S3Folder folder) throws IOException, BackendException {
		List<S3Node> result = new ArrayList<>();

		ListObjectsV2Result objectListing = client().listObjectsV2(cloud.s3Bucket(), folder.getPath());
		for (S3ObjectSummary objectSummary : objectListing.getObjectSummaries()) {
			result.add(S3CloudNodeFactory.from(folder, objectSummary));
		}
		return result;
	}

	public S3Folder create(S3Folder folder) throws IOException, BackendException {
		if (!exists(folder.getParent())) {
			folder = new S3Folder( //
					create(folder.getParent()), //
					folder.getName(), folder.getPath() //
			);
		}

		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setContentLength(0);

		InputStream emptyContent = new ByteArrayInputStream(new byte[0]);

		PutObjectRequest putObjectRequest = new PutObjectRequest(cloud.s3Bucket(), folder.getPath() + SUFFIX, emptyContent, metadata);
		client().putObject(putObjectRequest);

		return S3CloudNodeFactory.folder(folder.getParent(), folder.getName());
	}

	public S3Node move(S3Node source, S3Node target) throws IOException, BackendException {
		if (exists(target)) {
			throw new CloudNodeAlreadyExistsException(target.getName());
		}

		try {
			if (source instanceof S3Folder) {
				return S3CloudNodeFactory.from(target.getParent(), client().moveFolder(source.getPath(), target.getPath()).execute());
			} else {
				return S3CloudNodeFactory.from(target.getParent(), client().moveFile(source.getPath(), target.getPath()).execute());
			}
		} catch (ApiError ex) {
			if (PCloudApiError.isCloudNodeAlreadyExistsException(ex.errorCode())) {
				throw new CloudNodeAlreadyExistsException(target.getName());
			} else if (PCloudApiError.isNoSuchCloudFileException(ex.errorCode())) {
				throw new NoSuchCloudFileException(source.getName());
			} else {
				handleApiError(ex, PCloudApiError.ignoreMoveSet, null);
			}
			throw new FatalBackendException(ex);
		}
	}

	public S3File write(S3File file, DataSource data, final ProgressAware<UploadState> progressAware, boolean replace, long size) throws IOException, BackendException {
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

		return S3CloudNodeFactory.file(file.getParent(), uploadedFile);

	}

	private RemoteFile uploadFile(final S3File file, DataSource data, final ProgressAware<UploadState> progressAware, UploadOptions uploadOptions, final long size) //
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
			handleApiError(ex, file.getName());
			throw new FatalBackendException(ex);
		}
	}

	public void read(S3File file, Optional<File> encryptedTmpFile, OutputStream data, final ProgressAware<DownloadState> progressAware) throws IOException, BackendException {
		progressAware.onProgress(Progress.started(DownloadState.download(file)));

		Optional<String> cacheKey = Optional.empty();
		Optional<File> cacheFile = Optional.empty();

		RemoteFile remoteFile;

		if (sharedPreferencesHandler.useLruCache() && createLruCache(sharedPreferencesHandler.lruCacheSize())) {
			try {
				remoteFile = client().loadFile(file.getPath()).execute().asFile();
				cacheKey = Optional.of(remoteFile.fileId() + remoteFile.hash());
			} catch (ApiError ex) {
				handleApiError(ex, file.getName());
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

	private void writeToData(final S3File file, //
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
		} catch (ApiError ex) {
			handleApiError(ex, file.getName());
		}

		if (sharedPreferencesHandler.useLruCache() && encryptedTmpFile.isPresent() && cacheKey.isPresent()) {
			try {
				storeToLruCache(diskLruCache, cacheKey.get(), encryptedTmpFile.get());
			} catch (IOException e) {
				Timber.tag("PCloudImpl").e(e, "Failed to write downloaded file in LRU cache");
			}
		}

	}

	public void delete(S3Node node) throws IOException, BackendException {
		try {
			if (node instanceof S3Folder) {
				client() //
						.deleteFolder(node.getPath(), true).execute();
			} else {
				client() //
						.deleteFile(node.getPath()).execute();
			}
		} catch (ApiError ex) {
			handleApiError(ex, node.getName());
		}
	}

	public String currentAccount() throws IOException, BackendException {
		try {
			UserInfo currentAccount = client() //
					.getUserInfo() //
					.execute();
			return currentAccount.email();
		} catch (ApiError ex) {
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
		handleApiError(ex, null, null);
	}

	private void handleApiError(ApiError ex, String name) throws BackendException {
		handleApiError(ex, null, name);
	}

	private void handleApiError(ApiError ex, Set<Integer> errorCodes, String name) throws BackendException {
		if (errorCodes == null || !errorCodes.contains(ex.errorCode())) {
			int errorCode = ex.errorCode();
			if (PCloudApiError.isCloudNodeAlreadyExistsException(errorCode)) {
				throw new CloudNodeAlreadyExistsException(name);
			} else if (PCloudApiError.isForbiddenException(errorCode)) {
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
