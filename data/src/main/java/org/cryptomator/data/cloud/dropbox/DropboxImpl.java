package org.cryptomator.data.cloud.dropbox;

import android.content.Context;

import com.dropbox.core.DbxException;
import com.dropbox.core.NetworkIOException;
import com.dropbox.core.RetryException;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.CommitInfo;
import com.dropbox.core.v2.files.CreateFolderResult;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.FolderMetadata;
import com.dropbox.core.v2.files.GetMetadataErrorException;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.Metadata;
import com.dropbox.core.v2.files.RelocationResult;
import com.dropbox.core.v2.files.UploadSessionCursor;
import com.dropbox.core.v2.files.UploadSessionFinishErrorException;
import com.dropbox.core.v2.files.UploadSessionLookupErrorException;
import com.dropbox.core.v2.files.WriteMode;
import com.dropbox.core.v2.users.FullAccount;
import com.tomclaw.cache.DiskLruCache;

import org.cryptomator.data.util.TransferredBytesAwareInputStream;
import org.cryptomator.data.util.TransferredBytesAwareOutputStream;
import org.cryptomator.domain.CloudFile;
import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.CloudNode;
import org.cryptomator.domain.DropboxCloud;
import org.cryptomator.domain.exception.CloudNodeAlreadyExistsException;
import org.cryptomator.domain.exception.FatalBackendException;
import org.cryptomator.domain.exception.authentication.AuthenticationException;
import org.cryptomator.domain.exception.authentication.NoAuthenticationProvidedException;
import org.cryptomator.domain.usecases.ProgressAware;
import org.cryptomator.domain.usecases.cloud.DataSource;
import org.cryptomator.domain.usecases.cloud.DownloadState;
import org.cryptomator.domain.usecases.cloud.Progress;
import org.cryptomator.domain.usecases.cloud.UploadState;
import org.cryptomator.util.Optional;
import org.cryptomator.util.SharedPreferencesHandler;
import org.cryptomator.util.crypto.CredentialCryptor;
import org.cryptomator.util.file.LruFileCacheUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

import static org.cryptomator.domain.usecases.cloud.Progress.progress;
import static org.cryptomator.util.file.LruFileCacheUtil.Cache.DROPBOX;
import static org.cryptomator.util.file.LruFileCacheUtil.retrieveFromLruCache;
import static org.cryptomator.util.file.LruFileCacheUtil.storeToLruCache;

class DropboxImpl {

	private static final long CHUNKED_UPLOAD_CHUNK_SIZE = 8L << 20;
	private static final int CHUNKED_UPLOAD_MAX_ATTEMPTS = 5;

	private final DropboxClientFactory clientFactory = new DropboxClientFactory();
	private final DropboxCloud cloud;
	private final RootDropboxFolder root;
	private final Context context;
	private final SharedPreferencesHandler sharedPreferencesHandler;

	private DiskLruCache diskLruCache;

	DropboxImpl(DropboxCloud cloud, Context context) {
		if (cloud.accessToken() == null) {
			throw new NoAuthenticationProvidedException(cloud);
		}
		this.cloud = cloud;
		this.root = new RootDropboxFolder(cloud);
		this.context = context;

		sharedPreferencesHandler = new SharedPreferencesHandler(context);
	}

	private DbxClientV2 client() throws AuthenticationException {
		return clientFactory.getClient(decrypt(cloud.accessToken()), context);
	}

	private String decrypt(String password) {
		return CredentialCryptor //
				.getInstance(context) //
				.decrypt(password);
	}

	public DropboxFolder root() {
		return root;
	}

	public DropboxFolder resolve(String path) {
		if (path.startsWith("/")) {
			path = path.substring(1);
		}
		String[] names = path.split("/");
		DropboxFolder folder = root;
		for (String name : names) {
			folder = folder(folder, name);
		}
		return folder;
	}

	public DropboxFile file(CloudFolder folder, String name) {
		return file(folder, name, Optional.empty());
	}

	public DropboxFile file(CloudFolder folder, String name, Optional<Long> size) {
		return DropboxCloudNodeFactory.file( //
				(DropboxFolder) folder, //
				name, //
				size, //
				folder.getPath() + '/' + name);
	}

	public DropboxFolder folder(CloudFolder folder, String name) {
		return DropboxCloudNodeFactory.folder( //
				(DropboxFolder) folder, //
				name, //
				folder.getPath() + '/' + name);
	}

	public boolean exists(CloudNode node) throws AuthenticationException, DbxException {
		try {
			Metadata metadata = client() //
					.files() //
					.getMetadata(node.getPath());
			if (node instanceof CloudFolder) {
				return metadata instanceof FolderMetadata;
			} else {
				return metadata instanceof FileMetadata;
			}
		} catch (GetMetadataErrorException e) {
			if (e.errorValue.isPath()) {
				return false;
			}
			throw e;
		}
	}

	public List<DropboxNode> list(CloudFolder folder) throws AuthenticationException, DbxException {
		List<DropboxNode> result = new ArrayList<>();
		ListFolderResult listFolderResult = null;
		do {
			if (listFolderResult == null) {
				listFolderResult = client() //
						.files() //
						.listFolder(folder.getPath());
			} else {
				String cursor = listFolderResult.getCursor();
				listFolderResult = client() //
						.files() //
						.listFolderContinue(cursor);
			}
			List<Metadata> entryMetadata = listFolderResult.getEntries();
			for (Metadata metadata : entryMetadata) {
				result.add(DropboxCloudNodeFactory.from( //
						(DropboxFolder) folder, //
						metadata));
			}
		} while (listFolderResult.getHasMore());
		return result;
	}

	public DropboxFolder create(CloudFolder folder) throws AuthenticationException, DbxException {
		CreateFolderResult createFolderResult = client() //
				.files() //
				.createFolderV2(folder.getPath());

		return DropboxCloudNodeFactory.from( //
				(DropboxFolder) folder.getParent(), //
				createFolderResult.getMetadata());
	}

	public CloudNode move(CloudNode source, CloudNode target) throws AuthenticationException, DbxException {
		RelocationResult relocationResult = client() //
				.files() //
				.moveV2(source.getPath(), target.getPath());

		return DropboxCloudNodeFactory.from( //
				(DropboxFolder) target.getParent(), //
				relocationResult.getMetadata());
	}

	public DropboxFile write(DropboxFile file, DataSource data, final ProgressAware<UploadState> progressAware, boolean replace, long size)
			throws AuthenticationException, DbxException, IOException, CloudNodeAlreadyExistsException {
		if (exists(file) && !replace) {
			throw new CloudNodeAlreadyExistsException("CloudNode already exists and replace is false");
		}

		progressAware.onProgress(Progress.started(UploadState.upload(file)));
		WriteMode writeMode = WriteMode.ADD;
		if (replace) {
			writeMode = WriteMode.OVERWRITE;
		}
		// "Upload the file with simple upload API if it is small enough, otherwise use chunked
		// upload API for better performance. Arbitrarily chose 2 times our chunk size as the
		// deciding factor. This should really depend on your network."
		// Source: https://github.com/dropbox/dropbox-sdk-java/blob/master/examples/upload-file/src/main/java/com/dropbox/core/examples/upload_file/Main.java
		if (size <= (2 * CHUNKED_UPLOAD_CHUNK_SIZE)) {
			uploadFile(file, data, progressAware, writeMode, size);
		} else {
			chunkedUploadFile(file, data, progressAware, writeMode, size);
		}
		FileMetadata metadata = (FileMetadata) client() //
				.files() //
				.getMetadata(file.getPath());

		progressAware.onProgress(Progress.completed(UploadState.upload(file)));

		return DropboxCloudNodeFactory.from( //
				file.getParent(), //
				metadata);
	}

	private void uploadFile(final DropboxFile file, DataSource data, final ProgressAware<UploadState> progressAware, WriteMode writeMode, final long size) //
			throws AuthenticationException, DbxException, IOException {
		try (TransferredBytesAwareInputStream in = new TransferredBytesAwareInputStream(data.open(context)) {
			@Override
			public void bytesTransferred(long transferred) {
				progressAware.onProgress( //
						progress(UploadState.upload(file)) //
								.between(0) //
								.and(size) //
								.withValue(transferred));
			}
		}) {
			client() //
					.files() //
					.uploadBuilder(file.getPath()) //
					.withMode(writeMode) //
					.uploadAndFinish(in);
		}
	}

	private void chunkedUploadFile(final DropboxFile file, DataSource data, final ProgressAware<UploadState> progressAware, WriteMode writeMode, final long size)
			throws AuthenticationException, DbxException, IOException {
		// Assert our file is at least the chunk upload size. We make this assumption in the code
		// below to simplify the logic.
		if (size < CHUNKED_UPLOAD_CHUNK_SIZE) {
			throw new FatalBackendException("File too small, use uploadFile() instead.");
		}

		long uploaded = 0L;
		DbxException thrown = null;

		try (InputStream stream = data.open(context)) {

			// Chunked uploads have 3 phases, each of which can accept uploaded bytes:
			//
			// (1) Start: initiate the upload and get an upload session ID
			// (2) Append: upload chunks of the file to append to our session
			// (3) Finish: commit the upload and close the session
			//
			// We track how many bytes we uploaded to determine which phase we should be in.
			String sessionId = null;
			for (int i = 0; i < CHUNKED_UPLOAD_MAX_ATTEMPTS; i++) {
				if (i > 0) {
					Timber.v("Retrying chunked upload (" + (i + 1) + " / " + CHUNKED_UPLOAD_MAX_ATTEMPTS + " attempts)");
				}

				try {
					// if this is a retry, make sure seek to the correct offset
					stream.skip(uploaded);

					// (1) Start
					if (sessionId == null) {
						sessionId = client() //
								.files() //
								.uploadSessionStart() //
								.uploadAndFinish(new TransferredBytesAwareInputStream(stream) {
									@Override
									public void bytesTransferred(long transferred) {
										progressAware.onProgress( //
												progress(UploadState.upload(file)) //
														.between(0) //
														.and(size) //
														.withValue(transferred));
									}
								}, CHUNKED_UPLOAD_CHUNK_SIZE).getSessionId();
						uploaded += CHUNKED_UPLOAD_CHUNK_SIZE;

						progressAware.onProgress( //
								progress(UploadState.upload(file)) //
										.between(0) //
										.and(size) //
										.withValue(uploaded));
					}

					UploadSessionCursor cursor = new UploadSessionCursor(sessionId, uploaded);

					// (2) Append
					while ((size - uploaded) > CHUNKED_UPLOAD_CHUNK_SIZE) {
						final long fullyUploaded = uploaded;
						client() //
								.files() //
								.uploadSessionAppendV2(cursor) //
								.uploadAndFinish(new TransferredBytesAwareInputStream(stream) {
									@Override
									public void bytesTransferred(long transferred) {
										progressAware.onProgress( //
												progress(UploadState.upload(file)) //
														.between(0) //
														.and(size) //
														.withValue(fullyUploaded + transferred));
									}
								}, CHUNKED_UPLOAD_CHUNK_SIZE);
						uploaded += CHUNKED_UPLOAD_CHUNK_SIZE;

						progressAware.onProgress( //
								progress(UploadState.upload(file)) //
										.between(0) //
										.and(size) //
										.withValue(uploaded));

						cursor = new UploadSessionCursor(sessionId, uploaded);
					}

					// (3) Finish
					long remaining = size - uploaded;
					CommitInfo commitInfo = CommitInfo //
							.newBuilder(file.getPath()) //
							.withMode(writeMode) //
							.build();

					client() //
							.files() //
							.uploadSessionFinish(cursor, commitInfo) //
							.uploadAndFinish(stream, remaining);

					return;
				} catch (RetryException ex) {
					thrown = ex;
					// RetryExceptions are never automatically retried by the client for uploads. Must
					// catch this exception even if DbxRequestConfig.getMaxRetries() > 0.
					sleepQuietly(ex.getBackoffMillis());
				} catch (NetworkIOException ex) {
					thrown = ex;
					// Network issue with Dropbox (maybe a timeout?), try again.
				} catch (UploadSessionLookupErrorException ex) {
					if (ex.errorValue.isIncorrectOffset()) {
						thrown = ex;
						// Server offset into the stream doesn't match our offset (uploaded). Seek to
						// the expected offset according to the server and try again.
						uploaded = ex. //
								errorValue. //
										getIncorrectOffsetValue(). //
										getCorrectOffset();
					} else {
						throw new FatalBackendException(ex);
					}
				} catch (UploadSessionFinishErrorException ex) {
					if (ex.errorValue.isLookupFailed() && ex.errorValue.getLookupFailedValue().isIncorrectOffset()) {
						thrown = ex;
						// Server offset into the stream doesn't match our offset (uploaded). Seek to
						// the expected offset according to the server and try again.
						uploaded = ex. //
								errorValue. //
										getLookupFailedValue(). //
										getIncorrectOffsetValue(). //
										getCorrectOffset();
					} else {
						throw new FatalBackendException(ex);
					}
				}
			}
		}

		throw new FatalBackendException("Maxed out upload attempts to Dropbox.", thrown);
	}

	public void read(CloudFile file, Optional<File> encryptedTmpFile, OutputStream data, final ProgressAware<DownloadState> progressAware) throws DbxException, IOException {
		progressAware.onProgress(Progress.started(DownloadState.download(file)));

		Optional<String> cacheKey = Optional.empty();
		Optional<File> cacheFile = Optional.empty();

		if (sharedPreferencesHandler.useLruCache() && createLruCache(sharedPreferencesHandler.lruCacheSize())) {
			final FileMetadata fileMetadata = (FileMetadata) client() //
					.files() //
					.getMetadata(file.getPath());
			cacheKey = Optional.of(fileMetadata.getId() + fileMetadata.getRev());
			java.io.File cachedFile = diskLruCache.get(cacheKey.get());
			cacheFile = cachedFile != null ? Optional.of(cachedFile) : Optional.empty();
		}

		if (sharedPreferencesHandler.useLruCache() && cacheFile.isPresent()) {
			try {
				retrieveFromLruCache(cacheFile.get(), data);
			} catch (IOException e) {
				Timber.tag("DropboxImpl").w(e, "Error while retrieving content from Cache, get from web request");
				writeToData(file, data, encryptedTmpFile, cacheKey, progressAware);
			}
		} else {
			writeToData(file, data, encryptedTmpFile, cacheKey, progressAware);
		}

		progressAware.onProgress(Progress.completed(DownloadState.download(file)));
	}

	private void writeToData(final CloudFile file, //
			final OutputStream data, //
			final Optional<File> encryptedTmpFile, //
			final Optional<String> cacheKey, //
			final ProgressAware<DownloadState> progressAware) throws DbxException, IOException {
		try (TransferredBytesAwareOutputStream out = new TransferredBytesAwareOutputStream(data) {
			@Override
			public void bytesTransferred(long transferred) {
				progressAware.onProgress( //
						progress(DownloadState.download(file)) //
								.between(0) //
								.and(file.getSize().orElse(Long.MAX_VALUE)) //
								.withValue(transferred));
			}
		}) {
			client() //
					.files() //
					.download(file.getPath()) //
					.download(out);
		}

		if (sharedPreferencesHandler.useLruCache() && encryptedTmpFile.isPresent() && cacheKey.isPresent()) {
			try {
				storeToLruCache(diskLruCache, cacheKey.get(), encryptedTmpFile.get());
			} catch (IOException e) {
				Timber.tag("DropboxImpl").e(e, "Failed to write downloaded file in LRU cache");
			}
		}
	}

	private boolean createLruCache(int cacheSize) {
		if (diskLruCache == null) {
			try {
				diskLruCache = DiskLruCache.create(new LruFileCacheUtil(context).resolve(DROPBOX), cacheSize);
			} catch (IOException e) {
				Timber.tag("DropboxImpl").e(e, "Failed to setup LRU cache");
				return false;
			}
		}

		return true;
	}

	public void delete(CloudNode node) throws AuthenticationException, DbxException {
		client() //
				.files() //
				.deleteV2(node.getPath());
	}

	public String currentAccount() throws AuthenticationException, DbxException {
		FullAccount currentAccount = client() //
				.users() //
				.getCurrentAccount();
		return currentAccount.getName().getDisplayName();
	}

	private static void sleepQuietly(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException ex) {
			throw new FatalBackendException("Error uploading to Dropbox: interrupted during backoff.");
		}
	}
}
