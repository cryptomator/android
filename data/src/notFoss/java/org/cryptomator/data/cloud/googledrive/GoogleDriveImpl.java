package org.cryptomator.data.cloud.googledrive;

import static org.cryptomator.data.cloud.googledrive.GoogleDriveCloudNodeFactory.from;
import static org.cryptomator.data.cloud.googledrive.GoogleDriveCloudNodeFactory.isFolder;
import static org.cryptomator.domain.usecases.cloud.Progress.progress;
import static org.cryptomator.util.file.LruFileCacheUtil.retrieveFromLruCache;
import static org.cryptomator.util.file.LruFileCacheUtil.storeToLruCache;
import static org.cryptomator.util.file.LruFileCacheUtil.Cache.GOOGLE_DRIVE;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.cryptomator.data.util.TransferredBytesAwareOutputStream;
import org.cryptomator.domain.CloudNode;
import org.cryptomator.domain.GoogleDriveCloud;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.CloudNodeAlreadyExistsException;
import org.cryptomator.domain.exception.NoSuchCloudFileException;
import org.cryptomator.domain.exception.authentication.NoAuthenticationProvidedException;
import org.cryptomator.domain.usecases.ProgressAware;
import org.cryptomator.domain.usecases.cloud.DataSource;
import org.cryptomator.domain.usecases.cloud.DownloadState;
import org.cryptomator.domain.usecases.cloud.Progress;
import org.cryptomator.domain.usecases.cloud.UploadState;
import org.cryptomator.util.Optional;
import org.cryptomator.util.SharedPreferencesHandler;
import org.cryptomator.util.file.LruFileCacheUtil;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpResponseException;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.About;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.Revision;
import com.google.api.services.drive.model.RevisionList;
import com.tomclaw.cache.DiskLruCache;

import android.content.Context;

import timber.log.Timber;

class GoogleDriveImpl {

	private static final int STATUS_REQUEST_RANGE_NOT_SATISFIABLE = 416;

	private final GoogleDriveIdCache idCache;

	private final Context context;
	private final GoogleDriveCloud googleDriveCloud;
	private final SharedPreferencesHandler sharedPreferencesHandler;
	private final RootGoogleDriveFolder root;

	private DiskLruCache diskLruCache;

	GoogleDriveImpl(Context context, GoogleDriveCloud googleDriveCloud, GoogleDriveIdCache idCache) {
		if (googleDriveCloud.accessToken() == null) {
			throw new NoAuthenticationProvidedException(googleDriveCloud);
		}
		this.context = context;
		this.googleDriveCloud = googleDriveCloud;
		this.idCache = idCache;
		this.root = new RootGoogleDriveFolder(googleDriveCloud);

		sharedPreferencesHandler = new SharedPreferencesHandler(context);
	}

	private Drive client() {
		return new GoogleDriveClientFactory(context) //
				.getClient(googleDriveCloud.accessToken());
	}

	public GoogleDriveFolder root() {
		return root;
	}

	public GoogleDriveFolder resolve(String path) throws IOException {
		if (path.startsWith("/")) {
			path = path.substring(1);
		}
		String[] names = path.split("/");
		GoogleDriveFolder folder = root;
		for (String name : names) {
			folder = folder(folder, name);
		}
		return folder;
	}

	private Optional<File> findFile(String parentDriveId, String name) throws IOException {
		Drive.Files.List fileListQuery = client().files().list() //
				.setFields("files(id,mimeType,name,size)") //
				.setSupportsAllDrives(true);

		if (parentDriveId != null && parentDriveId.equals("root")) {
			fileListQuery.setQ("name contains '" + name + "' and '" + parentDriveId + "' in parents and trashed = false or sharedWithMe");
		} else {
			fileListQuery.setQ("name contains '" + name + "' and '" + parentDriveId + "' in parents and trashed = false");
		}

		FileList files = fileListQuery.execute();

		for (File file : files.getFiles()) {
			if (name.equals(file.getName())) {
				return Optional.of(file);
			}
		}
		return Optional.empty();
	}

	public GoogleDriveFile file(GoogleDriveFolder parent, String name) throws IOException {
		return file(parent, name, Optional.empty());
	}

	public GoogleDriveFile file(GoogleDriveFolder parent, String name, Optional<Long> size) throws IOException {
		if (parent.getDriveId() == null) {
			return GoogleDriveCloudNodeFactory.file(parent, name, size);
		}
		String path = GoogleDriveCloudNodeFactory.getNodePath(parent, name);
		GoogleDriveIdCache.NodeInfo nodeInfo = idCache.get(path);
		if (nodeInfo != null && !nodeInfo.isFolder()) {
			return GoogleDriveCloudNodeFactory.file( //
					parent, //
					name, //
					size, //
					path, //
					nodeInfo.getId());
		}

		Optional<File> file = findFile(parent.getDriveId(), name);
		if (file.isPresent()) {
			if (!isFolder(file.get())) {
				return idCache.cache(GoogleDriveCloudNodeFactory.file(parent, file.get()));
			}
		}

		return GoogleDriveCloudNodeFactory.file(parent, name, size);
	}

	public GoogleDriveFolder folder(GoogleDriveFolder parent, String name) throws IOException {
		if (parent.getDriveId() == null) {
			return GoogleDriveCloudNodeFactory.folder(parent, name);
		}
		String path = GoogleDriveCloudNodeFactory.getNodePath(parent, name);
		GoogleDriveIdCache.NodeInfo nodeInfo = idCache.get(path);
		if (nodeInfo != null && nodeInfo.isFolder()) {
			return GoogleDriveCloudNodeFactory.folder( //
					parent, //
					name, //
					path, //
					nodeInfo.getId());
		}
		Optional<File> folder = findFile(parent.getDriveId(), name);
		if (folder.isPresent()) {
			if (isFolder(folder.get())) {
				return idCache.cache( //
						GoogleDriveCloudNodeFactory.folder(parent, folder.get()));
			}
		}

		return GoogleDriveCloudNodeFactory.folder(parent, name);
	}

	public boolean exists(GoogleDriveNode node) throws IOException {
		try {
			Optional<File> file = findFile( //
					node.getParent().getDriveId(), //
					node.getName());
			boolean fileExists = file.isPresent();
			if (fileExists) {
				idCache.add(from( //
						node.getParent(), //
						file.get()));
			}
			return fileExists;
		} catch (GoogleJsonResponseException e) {
			return false;
		}
	}

	public List<CloudNode> list(GoogleDriveFolder folder) throws IOException {
		List<CloudNode> result = new ArrayList<>();
		String pageToken = null;
		do {
			Drive.Files.List fileListQuery = client() //
					.files() //
					.list() //
					.setFields("nextPageToken,files(id,mimeType,modifiedTime,name,size)") //
					.setPageSize(1000) //
					.setSupportsAllDrives(true).setIncludeItemsFromAllDrives(true).setPageToken(pageToken);

			if (folder.getDriveId().equals("root")) {
				fileListQuery.setQ("'" + folder.getDriveId() + "' in parents and trashed = false or sharedWithMe");
			} else {
				fileListQuery.setQ("'" + folder.getDriveId() + "' in parents and trashed = false");
			}

			FileList fileList = fileListQuery.execute();

			for (File file : fileList.getFiles()) {
				result.add(idCache.cache(from(folder, file)));
			}
			pageToken = fileList.getNextPageToken();
		} while (pageToken != null);
		return result;
	}

	public GoogleDriveFolder create(GoogleDriveFolder folder) throws IOException {
		if (folder.getParent().getDriveId() == null) {
			folder = new GoogleDriveFolder( //
					create(folder.getParent()), //
					folder.getName(), //
					folder.getPath(), //
					folder.getDriveId());
		}
		File metadata = new File();
		metadata.setName(folder.getName());
		metadata.setMimeType("application/vnd.google-apps.folder");
		metadata.setParents( //
				Collections.singletonList(folder.getParent().getDriveId()));

		File createdFolder = client() //
				.files() //
				.create(metadata) //
				.setFields("id,name") //
				.setSupportsAllDrives(true).execute();

		return idCache.cache( //
				GoogleDriveCloudNodeFactory.folder( //
						folder.getParent(), //
						createdFolder));
	}

	public GoogleDriveNode move(GoogleDriveNode source, GoogleDriveNode target) throws IOException, CloudNodeAlreadyExistsException {
		if (exists(target)) {
			throw new CloudNodeAlreadyExistsException(target.getName());
		}

		File metadata = new File();
		metadata.setName(target.getName());

		File movedFile = client() //
				.files() //
				.update(source.getDriveId(), metadata) //
				.setFields("id,mimeType,modifiedTime,name,size") //
				.setAddParents(target.getParent().getDriveId()) //
				.setRemoveParents(source.getParent().getDriveId()) //
				.setSupportsAllDrives(true).execute();

		idCache.remove(source);
		return idCache.cache(from(target.getParent(), movedFile));
	}

	public GoogleDriveFile write(final GoogleDriveFile file, DataSource data, final ProgressAware<UploadState> progressAware, boolean replace, final long size) //
			throws IOException, BackendException {
		if (exists(file) && !replace) {
			throw new CloudNodeAlreadyExistsException("CloudNode already exists and replace is false");
		}

		if (file.getParent().getDriveId() == null) {
			throw new NoSuchCloudFileException(String.format("The parent folder of %s doesn't have a driveId. The file would remain in root folder", file.getPath()));
		}

		File metadata = new File();
		metadata.setName(file.getName());

		progressAware.onProgress(Progress.started(UploadState.upload(file)));
		File uploadedFile;
		if (file.getDriveId() != null && replace) {
			try (TransferredBytesAwareGoogleContentInputStream in = new TransferredBytesAwareGoogleContentInputStream(null, data.open(context), size) {
				@Override
				public void bytesTransferred(long transferred) {
					progressAware.onProgress( //
							progress(UploadState.upload(file)) //
									.between(0) //
									.and(size) //
									.withValue(transferred));
				}
			}) {
				uploadedFile = client() //
						.files() //
						.update( //
								file.getDriveId(), //
								metadata, //
								in)
						.setFields("id,modifiedTime,name,size") //
						.setSupportsAllDrives(true) //
						.execute();
			}
		} else {
			metadata.setParents( //
					Collections.singletonList(file.getParent().getDriveId()));

			try (TransferredBytesAwareGoogleContentInputStream in = new TransferredBytesAwareGoogleContentInputStream(null, data.open(context), size) {
				@Override
				public void bytesTransferred(long transferred) {
					progressAware.onProgress( //
							progress(UploadState.upload(file)) //
									.between(0) //
									.and(size) //
									.withValue(transferred));
				}
			}) {
				uploadedFile = client() //
						.files() //
						.create(metadata, in).setFields("id,modifiedTime,name,size") //
						.setSupportsAllDrives(true) //
						.execute();
			}
		}
		progressAware.onProgress(Progress.completed(UploadState.upload(file)));
		return idCache.cache( //
				GoogleDriveCloudNodeFactory.file(file.getParent(), uploadedFile));
	}

	public void read(final GoogleDriveFile file, Optional<java.io.File> encryptedTmpFile, OutputStream data, final ProgressAware<DownloadState> progressAware) throws IOException {
		progressAware.onProgress(Progress.started(DownloadState.download(file)));

		Optional<String> cacheKey = Optional.empty();
		Optional<java.io.File> cacheFile = Optional.empty();

		if (sharedPreferencesHandler.useLruCache() && createLruCache(sharedPreferencesHandler.lruCacheSize())) {
			List<Revision> revisions = new ArrayList<>();
			String pageToken = null;
			do {
				final RevisionList revisionList = client() //
						.revisions() //
						.list(file.getDriveId()) //
						.setPageToken(pageToken).execute(); //

				revisions.addAll(revisionList.getRevisions());

				pageToken = revisionList.getNextPageToken();
			} while (pageToken != null);

			Collections.sort(revisions, (revision1, revision2) -> {
				Long modified1 = revision1.getModifiedTime().getValue();
				Long modified2 = revision2.getModifiedTime().getValue();
				return Integer.compare(modified1.compareTo(modified2), 0);
			});

			int revisionIndex = revisions.size() > 0 ? revisions.size() - 1 : 0;
			cacheKey = Optional.of(file.getDriveId() + revisions.get(revisionIndex).getId());
			java.io.File cachedFile = diskLruCache.get(cacheKey.get());
			cacheFile = cachedFile != null ? Optional.of(cachedFile) : Optional.empty();
		}

		if (sharedPreferencesHandler.useLruCache() && cacheFile.isPresent()) {
			try {
				retrieveFromLruCache(cacheFile.get(), data);
			} catch (IOException e) {
				Timber.tag("GoogleDriveImpl").w(e, "Error while retrieving content from Cache, get from web request");
				writeToDate(file, data, encryptedTmpFile, cacheKey, progressAware);
			}
		} else {
			writeToDate(file, data, encryptedTmpFile, cacheKey, progressAware);
		}

		progressAware.onProgress(Progress.completed(DownloadState.download(file)));
	}

	private void writeToDate(final GoogleDriveFile file, //
			final OutputStream data, //
			final Optional<java.io.File> encryptedTmpFile, //
			final Optional<String> cacheKey, //
			final ProgressAware<DownloadState> progressAware) throws IOException {
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
					.get(file.getDriveId()) //
					.setAlt("media") //
					.setSupportsAllDrives(true) //
					.executeMediaAndDownloadTo(out);
		} catch (HttpResponseException e) {
			ignoreEmptyFileErrorAndRethrowOthers(e, file);
		}

		if (sharedPreferencesHandler.useLruCache() && encryptedTmpFile.isPresent() && cacheKey.isPresent()) {
			try {
				storeToLruCache(diskLruCache, cacheKey.get(), encryptedTmpFile.get());
			} catch (IOException e) {
				Timber.tag("GoogleDriveImpl").e(e, "Failed to write downloaded file in LRU cache");
			}
		}
	}

	private boolean createLruCache(int cacheSize) {
		if (diskLruCache == null) {
			try {
				diskLruCache = DiskLruCache.create(new LruFileCacheUtil(context).resolve(GOOGLE_DRIVE), cacheSize);
			} catch (IOException e) {
				Timber.tag("GoogleDriveImpl").e(e, "Failed to setup LRU cache");
				return false;
			}
		}

		return true;
	}

	/*
	 * Workaround a bug in gdrive which does not allow to download empty files.
	 *
	 * In this case an HttpResponseException with status code 416 is thrown. The filesize is checked.
	 * If zero, the exception is ignored - nothing has been read, so the OutputStream is in the correct
	 * state.
	 */
	private void ignoreEmptyFileErrorAndRethrowOthers(HttpResponseException e, GoogleDriveFile file) throws IOException {
		if (e.getStatusCode() == STATUS_REQUEST_RANGE_NOT_SATISFIABLE) {
			Optional<File> foundFile = findFile( //
					file.getParent().getDriveId(), //
					file.getName());
			if (sizeOfFile(foundFile) == 0) {
				return;
			}
		}
		throw e;
	}

	private long sizeOfFile(Optional<File> foundFile) {
		if (foundFile.isAbsent() || isFolder(foundFile.get())) {
			return -1;
		}
		return foundFile.get().getSize();
	}

	public void delete(GoogleDriveNode node) throws IOException {
		client().files().delete(node.getDriveId()).setSupportsAllDrives(true).execute();
		idCache.remove(node);
	}

	public String currentAccount() throws IOException {
		About about = client().about().get().execute();
		return about.getUser().getDisplayName();
	}

}
