package org.cryptomator.data.cloud.s3;

import android.content.Context;

import com.amazonaws.event.ProgressListener;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import com.amazonaws.services.s3.model.DeleteObjectsRequest.KeyVersion;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.Owner;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.pcloud.sdk.ApiError;
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
import java.util.List;
import java.util.Set;

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
		return S3CloudNodeFactory.file(parent, name, size, parent.getPath() + SUFFIX + name);
	}

	public S3Folder folder(S3Folder parent, String name) throws IOException, BackendException {
		return S3CloudNodeFactory.folder(parent, name, parent.getPath() + SUFFIX + name + SUFFIX);
	}

	public boolean exists(S3Node node) {
		String path = node.getPath();
		if (node instanceof S3Folder) {
			path += SUFFIX;
		}

		ObjectListing result = client().listObjects(cloud.s3Bucket(), path);

		if (result.getObjectSummaries().size() > 0) {
			return true;
		} else {
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

		PutObjectResult result = uploadFile(file, data, progressAware, size);

		progressAware.onProgress(Progress.completed(UploadState.upload(file)));

		return S3CloudNodeFactory.file(file.getParent(), file.getName(), result);

	}

	private PutObjectResult uploadFile(final S3File file, DataSource data, final ProgressAware<UploadState> progressAware, final long size) //
			throws IOException, BackendException {
		ProgressListener listener = progressEvent -> progressAware.onProgress( //
				progress(UploadState.upload(file)) //
						.between(0) //
						.and(size) //
						.withValue(progressEvent.getBytesTransferred()));

		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setContentLength(data.size(context).get());

		PutObjectRequest request = new PutObjectRequest(cloud.s3Bucket(), file.getPath(), data.open(context), metadata);
		request.setGeneralProgressListener(listener);

		return client().putObject(request);
	}

	public void read(S3File file, Optional<File> encryptedTmpFile, OutputStream data, final ProgressAware<DownloadState> progressAware) throws IOException, BackendException {
		progressAware.onProgress(Progress.started(DownloadState.download(file)));

		Optional<String> cacheKey = Optional.empty();
		Optional<File> cacheFile = Optional.empty();

		ObjectListing objectListing;

		if (sharedPreferencesHandler.useLruCache() && createLruCache(sharedPreferencesHandler.lruCacheSize())) {
			objectListing = client().listObjects(cloud.s3Bucket(), file.getPath());
			if (objectListing.getObjectSummaries().size() != 1) {
				throw new NoSuchCloudFileException(file.getPath());
			}
			S3ObjectSummary summary = objectListing.getObjectSummaries().get(0);
			cacheKey = Optional.of(summary.getKey() + summary.getETag());

			File cachedFile = diskLruCache.get(cacheKey.get());
			cacheFile = cachedFile != null ? Optional.of(cachedFile) : Optional.empty();
		}

		if (sharedPreferencesHandler.useLruCache() && cacheFile.isPresent()) {
			try {
				retrieveFromLruCache(cacheFile.get(), data);
			} catch (IOException e) {
				Timber.tag("S3Impl").w(e, "Error while retrieving content from Cache, get from web request");
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

		ProgressListener listener = progressEvent -> progressAware.onProgress( //
				progress(DownloadState.download(file)) //
						.between(0) //
						.and(file.getSize().orElse(Long.MAX_VALUE)) //
						.withValue(progressEvent.getBytesTransferred()));

		GetObjectRequest request = new GetObjectRequest(cloud.s3Bucket(), file.getPath());
		request.setGeneralProgressListener(listener);

		S3Object s3Object = client().getObject(request);

		CopyStream.copyStreamToStream(s3Object.getObjectContent(), data);

		if (sharedPreferencesHandler.useLruCache() && encryptedTmpFile.isPresent() && cacheKey.isPresent()) {
			try {
				storeToLruCache(diskLruCache, cacheKey.get(), encryptedTmpFile.get());
			} catch (IOException e) {
				Timber.tag("S3Impl").e(e, "Failed to write downloaded file in LRU cache");
			}
		}

	}

	public void delete(S3Node node) throws IOException, BackendException {
		if (node instanceof S3Folder) {
			ObjectListing listing = client().listObjects(cloud.s3Bucket(), node.getPath() + SUFFIX);
			List<KeyVersion> keys = new ArrayList<>();
			for (S3ObjectSummary summary : listing.getObjectSummaries()) {
				keys.add(new KeyVersion(summary.getKey()));
			}

			DeleteObjectsRequest request = new DeleteObjectsRequest(cloud.s3Bucket());
			request.withKeys(keys);

			client().deleteObjects(request);
		} else {
			client().deleteObject(cloud.s3Bucket(), node.getPath());
		}
	}

	public String currentAccount() {
		 Owner currentAccount = client() //
				.getS3AccountOwner();
		return currentAccount.getDisplayName();
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
