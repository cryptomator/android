package org.cryptomator.data.cloud.s3;

import android.content.Context;

import com.amazonaws.event.ProgressListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.mobileconnectors.s3.transferutility.UploadOptions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.CopyObjectResult;
import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import com.amazonaws.services.s3.model.DeleteObjectsRequest.KeyVersion;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.Owner;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.tomclaw.cache.DiskLruCache;

import org.cryptomator.data.util.CopyStream;
import org.cryptomator.domain.S3Cloud;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.CloudNodeAlreadyExistsException;
import org.cryptomator.domain.exception.FatalBackendException;
import org.cryptomator.domain.exception.ForbiddenException;
import org.cryptomator.domain.exception.NoSuchBucketException;
import org.cryptomator.domain.exception.NoSuchCloudFileException;
import org.cryptomator.domain.exception.UnauthorizedException;
import org.cryptomator.domain.exception.authentication.WrongCredentialsException;
import org.cryptomator.domain.usecases.ProgressAware;
import org.cryptomator.domain.usecases.cloud.DataSource;
import org.cryptomator.domain.usecases.cloud.DownloadState;
import org.cryptomator.domain.usecases.cloud.Progress;
import org.cryptomator.domain.usecases.cloud.UploadState;
import org.cryptomator.util.Optional;
import org.cryptomator.util.SharedPreferencesHandler;
import org.cryptomator.util.concurrent.CompletableFuture;
import org.cryptomator.util.file.LruFileCacheUtil;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import timber.log.Timber;

import static org.cryptomator.domain.usecases.cloud.Progress.progress;
import static org.cryptomator.util.file.LruFileCacheUtil.Cache.S3;
import static org.cryptomator.util.file.LruFileCacheUtil.retrieveFromLruCache;
import static org.cryptomator.util.file.LruFileCacheUtil.storeToLruCache;

class S3Impl {

	private static final long CHUNKED_UPLOAD_MAX_SIZE = 100L << 20;
	private static final String DELIMITER = "/";

	private final S3ClientFactory clientFactory = new S3ClientFactory();
	private final S3Cloud cloud;
	private final RootS3Folder root;
	private final Context context;

	private final SharedPreferencesHandler sharedPreferencesHandler;
	private DiskLruCache diskLruCache;

	S3Impl(Context context, S3Cloud cloud) {
		if (cloud.accessKey() == null || cloud.secretKey() == null) {
			throw new WrongCredentialsException(cloud);
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

	public S3Folder resolve(String path) {
		if (path.startsWith(DELIMITER)) {
			path = path.substring(1);
		}
		String[] names = path.split(DELIMITER);
		S3Folder folder = root;
		for (String name : names) {
			if (!name.isEmpty()) {
				folder = folder(folder, name);
			}
		}
		return folder;
	}

	public S3File file(S3Folder parent, String name) throws BackendException, IOException {
		return file(parent, name, Optional.empty());
	}

	public S3File file(S3Folder parent, String name, Optional<Long> size) throws BackendException, IOException {
		return S3CloudNodeFactory.file(parent, name, size, parent.getKey() + name);
	}

	public S3Folder folder(S3Folder parent, String name) {
		return S3CloudNodeFactory.folder(parent, name, parent.getKey() + name);
	}

	public void bucketExists() throws BackendException {
		if (!client().doesBucketExist(cloud.s3Bucket())) {
			throw new NoSuchBucketException(cloud.s3Bucket());
		}
	}

	public boolean exists(S3Node node) {
		String key = node.getKey();

		ListObjectsV2Result result = client().listObjectsV2(cloud.s3Bucket(), key);

		return result.getObjectSummaries().size() > 0;
	}

	public List<S3Node> list(S3Folder folder) throws IOException, BackendException {
		List<S3Node> result = new ArrayList<>();

		ListObjectsV2Request request = new ListObjectsV2Request().withBucketName(cloud.s3Bucket()).withPrefix(folder.getKey()).withDelimiter(DELIMITER);

		ListObjectsV2Result listObjects = client().listObjectsV2(request);
		for (String prefix : listObjects.getCommonPrefixes()) {
			// add folders
			result.add(S3CloudNodeFactory.folder(folder, S3CloudNodeFactory.getNameFromKey(prefix)));
		}

		for (S3ObjectSummary objectSummary : listObjects.getObjectSummaries()) {
			// add files but skip parent folder
			if (!objectSummary.getKey().equals(listObjects.getPrefix())) {
				result.add(S3CloudNodeFactory.file(folder, objectSummary));
			}
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

		try {
			PutObjectRequest putObjectRequest = new PutObjectRequest(cloud.s3Bucket(), folder.getKey(), emptyContent, metadata);
			client().putObject(putObjectRequest);
		} catch(AmazonS3Exception ex) {
			handleApiError(ex, folder.getName());
		}

		return S3CloudNodeFactory.folder(folder.getParent(), folder.getName());
	}

	public S3Node move(S3Node source, S3Node target) throws IOException, BackendException {
		if (exists(target)) {
			throw new CloudNodeAlreadyExistsException(target.getName());
		}

		if (source instanceof S3Folder) {
			ListObjectsV2Result listObjects = client().listObjectsV2(cloud.s3Bucket(), source.getPath());

			if (listObjects.getObjectSummaries().size() > 0) {

				List<DeleteObjectsRequest.KeyVersion> objectsToDelete = new ArrayList<>();

				for (S3ObjectSummary summary : listObjects.getObjectSummaries()) {
					objectsToDelete.add(new DeleteObjectsRequest.KeyVersion(summary.getKey()));
					String destinationKey = summary.getKey().replace(source.getPath(), target.getPath());

					client().copyObject(cloud.s3Bucket(), summary.getKey(), cloud.s3Bucket(), destinationKey);
				}
				client().deleteObjects(new DeleteObjectsRequest(cloud.s3Bucket()).withKeys(objectsToDelete));
			} else {
				throw new NoSuchCloudFileException(source.getPath());
			}
			return S3CloudNodeFactory.folder(target.getParent(), target.getName());
		} else {
			CopyObjectResult result = client().copyObject(cloud.s3Bucket(), source.getPath(), cloud.s3Bucket(), target.getPath());
			client().deleteObject(cloud.s3Bucket(), source.getPath());
			return S3CloudNodeFactory.file(target.getParent(), target.getName(), ((S3File) source).getSize(), Optional.of(result.getLastModifiedDate()));
		}
	}

	public S3File write(S3File file, DataSource data, final ProgressAware<UploadState> progressAware, boolean replace, long size) throws IOException, BackendException {
		if (!replace && exists(file)) {
			throw new CloudNodeAlreadyExistsException("CloudNode already exists and replace is false");
		}

		progressAware.onProgress(Progress.started(UploadState.upload(file)));

		final CompletableFuture<Optional<ObjectMetadata>> result = new CompletableFuture<>();

		try {
			if (size <= CHUNKED_UPLOAD_MAX_SIZE) {
				uploadFile(file, data, progressAware, result, size);
			} else {
				uploadChunkedFile(file, data, progressAware, result, size);
			}
		} catch(AmazonS3Exception ex) {
			handleApiError(ex, file.getName());
		}

		try {
			Optional<ObjectMetadata> objectMetadataOptional = result.get();
			ObjectMetadata objectMetadata = objectMetadataOptional.orElseGet(() -> client().getObjectMetadata(cloud.s3Bucket(), file.getPath()));
			progressAware.onProgress(Progress.completed(UploadState.upload(file)));
			return S3CloudNodeFactory.file(file.getParent(), file.getName(), objectMetadata);
		} catch (ExecutionException | InterruptedException e) {
			throw new FatalBackendException(e);
		}

	}

	private void uploadFile(final S3File file, DataSource data, final ProgressAware<UploadState> progressAware, CompletableFuture<Optional<ObjectMetadata>> result, final long size) //
			throws IOException {
		AtomicLong bytesTransferred = new AtomicLong(0);
		ProgressListener listener = progressEvent -> {
			bytesTransferred.set(bytesTransferred.get() + progressEvent.getBytesTransferred());
			progressAware.onProgress( //
					progress(UploadState.upload(file)) //
							.between(0) //
							.and(size) //
							.withValue(bytesTransferred.get()));
		};

		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setContentLength(data.size(context).get());

		PutObjectRequest request = new PutObjectRequest(cloud.s3Bucket(), file.getPath(), data.open(context), metadata);
		request.setGeneralProgressListener(listener);

		result.complete(Optional.of(client().putObject(request).getMetadata()));
	}

	private void uploadChunkedFile(final S3File file, DataSource data, final ProgressAware<UploadState> progressAware, CompletableFuture<Optional<ObjectMetadata>> result, final long size) //
			throws IOException {

		TransferUtility tu = TransferUtility //
				.builder() //
				.s3Client(client()) //
				.context(context) //
				.defaultBucket(cloud.s3Bucket()) //
				.build();

		TransferListener transferListener = new TransferListener() {
			@Override
			public void onStateChanged(int id, TransferState state) {
				if (state.equals(TransferState.COMPLETED)) {
					progressAware.onProgress(Progress.completed(UploadState.upload(file)));
					result.complete(Optional.empty());
				}
			}

			@Override
			public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
				progressAware.onProgress( //
						progress(UploadState.upload(file)) //
								.between(0) //
								.and(size) //
								.withValue(bytesCurrent));
			}

			@Override
			public void onError(int id, Exception ex) {
				result.fail(ex);
			}
		};

		UploadOptions uploadOptions = UploadOptions.builder().transferListener(transferListener).build();

		tu.upload(file.getPath(), data.open(context), uploadOptions);
	}

	public void read(S3File file, Optional<File> encryptedTmpFile, OutputStream data, final ProgressAware<DownloadState> progressAware) throws IOException, BackendException {
		progressAware.onProgress(Progress.started(DownloadState.download(file)));

		Optional<String> cacheKey = Optional.empty();
		Optional<File> cacheFile = Optional.empty();

		ListObjectsV2Result listObjects;

		if (sharedPreferencesHandler.useLruCache() && createLruCache(sharedPreferencesHandler.lruCacheSize())) {
			listObjects = client().listObjectsV2(cloud.s3Bucket(), file.getKey());
			if (listObjects.getObjectSummaries().size() != 1) {
				throw new NoSuchCloudFileException(file.getKey());
			}
			S3ObjectSummary summary = listObjects.getObjectSummaries().get(0);
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
		AtomicLong bytesTransferred = new AtomicLong(0);
		ProgressListener listener = progressEvent -> {
			bytesTransferred.set(bytesTransferred.get() + progressEvent.getBytesTransferred());

			progressAware.onProgress( //
					progress(DownloadState.download(file)) //
							.between(0) //
							.and(file.getSize().orElse(Long.MAX_VALUE)) //
							.withValue(bytesTransferred.get()));
		};

		GetObjectRequest request = new GetObjectRequest(cloud.s3Bucket(), file.getPath());
		request.setGeneralProgressListener(listener);

		try {
			S3Object s3Object = client().getObject(request);

			CopyStream.copyStreamToStream(s3Object.getObjectContent(), data);

			if (sharedPreferencesHandler.useLruCache() && encryptedTmpFile.isPresent() && cacheKey.isPresent()) {
				try {
					storeToLruCache(diskLruCache, cacheKey.get(), encryptedTmpFile.get());
				} catch (IOException e) {
					Timber.tag("S3Impl").e(e, "Failed to write downloaded file in LRU cache");
				}
			}
		} catch(AmazonS3Exception ex) {
			handleApiError(ex, file.getName());
		}

	}

	public void delete(S3Node node) throws IOException, BackendException {
		if (node instanceof S3Folder) {
			List<S3ObjectSummary> summaries = client().listObjectsV2(cloud.s3Bucket(), node.getPath()).getObjectSummaries();

			List<KeyVersion> keys = new ArrayList<>();
			for (S3ObjectSummary summary : summaries) {
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
				diskLruCache = DiskLruCache.create(new LruFileCacheUtil(context).resolve(S3), cacheSize);
			} catch (IOException e) {
				Timber.tag("S3Impl").e(e, "Failed to setup LRU cache");
				return false;
			}
		}

		return true;
	}

	private void handleApiError(AmazonS3Exception ex, String name) throws BackendException {
		String errorCode = ex.getErrorCode();
		if (S3CloudApiExceptions.isAccessProblem(errorCode)) {
			throw new ForbiddenException();
		}  else if (S3CloudApiErrorCodes.NO_SUCH_BUCKET.getValue().equals(errorCode)) {
			throw new NoSuchBucketException(name);
		} else if (S3CloudApiErrorCodes.NO_SUCH_KEY.getValue().equals(errorCode)) {
			throw new NoSuchCloudFileException(name);
		}  else {
			throw new FatalBackendException(ex);
		}
	}
}
