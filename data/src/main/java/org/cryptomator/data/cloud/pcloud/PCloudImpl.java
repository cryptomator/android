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

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import okhttp3.internal.concurrent.TaskRunner;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;
import okio.Source;

import static org.cryptomator.domain.usecases.cloud.Progress.progress;

class PCloudImpl {

	private final PCloudIdCache idCache;

	private final PCloudClientFactory clientFactory = new PCloudClientFactory();
	private final PCloud cloud;
	private final RootPCloudFolder root;
	private final Context context;

	private final String UTF_8 = "UTF-8";

	PCloudImpl(Context context, PCloud cloud, PCloudIdCache idCache) {
		if (cloud.accessToken() == null) {
			throw new NoAuthenticationProvidedException(cloud);
		}

		this.context = context;
		this.cloud = cloud;
		this.idCache = idCache;
		this.root = new RootPCloudFolder(cloud);
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

	private Optional<RemoteEntry> findEntry(Long folderId, String name, boolean isFolder) throws IOException, BackendException {
		try {
			RemoteFolder remoteFolder = client().listFolder(folderId).execute();
			for (RemoteEntry remoteEntry : remoteFolder.children()) {
				if (isFolder) {
					if (remoteEntry.isFolder() && remoteEntry.name().equals(name)) {
						return Optional.of(remoteEntry);
					}
				} else {
					if (remoteEntry.isFile() && remoteEntry.name().equals(name)) {
						return Optional.of(remoteEntry);
					}
				}
			}
			return Optional.empty();
		} catch(ApiError ex) {
			Set<Integer> ignoredErrorCodes = new HashSet<>();
			ignoredErrorCodes.add(PCloudApiError.PCloudApiErrorCodes.DIRECTORY_DOES_NOT_EXIST.getValue());
			handleApiError(ex, ignoredErrorCodes);
			return Optional.empty();
		}
	}

	public PCloudFile file(PCloudFolder parent, String name) throws BackendException, IOException {
		return file(parent, name, Optional.empty());
	}

	public PCloudFile file(PCloudFolder parent, String name, Optional<Long> size) throws BackendException, IOException {
		if (parent.getId() == null) {
			return PCloudNodeFactory.file(parent, name, size);
		}

		String path = PCloudNodeFactory.getNodePath(parent, name);
		PCloudIdCache.NodeInfo nodeInfo = idCache.get(path);
		if (nodeInfo != null && !nodeInfo.isFolder()) {
			return PCloudNodeFactory.file(parent, name, size, path, nodeInfo.getId());
		}

		Optional<RemoteEntry> file = findEntry(parent.getId(), name, false);
		if (file.isPresent()) {
			return idCache.cache(PCloudNodeFactory.file(parent, file.get().asFile()));
		}

		return PCloudNodeFactory.file(parent, name, size);
	}

	public PCloudFolder folder(PCloudFolder parent, String name) throws IOException, BackendException {
		if (parent.getId() == null) {
			return PCloudNodeFactory.folder(parent, name);
		}

		String path = PCloudNodeFactory.getNodePath(parent, name);
		PCloudIdCache.NodeInfo nodeInfo = idCache.get(path);
		if (nodeInfo != null && nodeInfo.isFolder()) {
			return PCloudNodeFactory.folder(parent, name, path, nodeInfo.getId());
		}

		Optional<RemoteEntry> folder = findEntry(parent.getId(), name, true);
		if (folder.isPresent()) {
			return idCache.cache(PCloudNodeFactory.folder(parent, folder.get().asFolder()));
		}
		return PCloudNodeFactory.folder(parent, name);
	}

	public boolean exists(PCloudNode node) throws IOException, BackendException {
		try {
			if (node instanceof PCloudFolder) {
				RemoteFolder remoteFolder = client().listFolder(node.getPath()).execute();
				idCache.add(PCloudNodeFactory.folder(node.getParent(), remoteFolder));
			} else {
				RemoteFile remoteFile = client().stat(node.getPath()).execute();
				idCache.add(PCloudNodeFactory.file(node.getParent(), remoteFile));
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

		Long folderId = folder.getId();
		RemoteFolder listFolderResult;
		try {
			if (folderId == null) {
				listFolderResult = client().listFolder(folder.getPath()).execute();
			} else {
				listFolderResult = client() //
						.listFolder(folder.getId()) //
						.execute();
			}
			List<RemoteEntry> entryMetadata = listFolderResult.children();
			for (RemoteEntry metadata : entryMetadata) {
				result.add(idCache.cache(PCloudNodeFactory.from(folder, metadata)));
			}
			return result;
		} catch(ApiError ex) {
			handleApiError(ex);
			throw new FatalBackendException(ex);
		}
	}

	public PCloudFolder create(PCloudFolder folder) throws IOException, BackendException  {
		if (folder.getParent().getId() == null) {
			folder = new PCloudFolder( //
					create(folder.getParent()), //
					folder.getName(), folder.getPath(), folder.getId() //
					//
			);
		}

		try {
			RemoteFolder createdFolder = client() //
					.createFolder(folder.getParent().getId(), folder.getName()) //
					.execute();
			return idCache.cache( //
					PCloudNodeFactory.folder(folder.getParent(), createdFolder));
		} catch (ApiError ex) {
			handleApiError(ex);
			throw new FatalBackendException(ex);
		}
	}

	public PCloudNode move(PCloudNode source, PCloudNode target) throws IOException, BackendException {
		if (exists(target)) {
			throw new CloudNodeAlreadyExistsException(target.getName());
		}

		RemoteEntry relocationResult;

		try {
			if (source instanceof PCloudFolder) {
				relocationResult = client().moveFolder(source.getId(), target.getParent().getId()).execute();
				if (!relocationResult.name().equals(target.getName())) {
					relocationResult = client().renameFolder(relocationResult.asFolder(), target.getName()).execute();
				}
			} else {
				relocationResult = client().moveFile(source.getId(), target.getParent().getId()).execute();
				if (!relocationResult.name().equals(target.getName())) {
					relocationResult = client().renameFile(relocationResult.asFile(), target.getName()).execute();
				}
			}

			idCache.remove(source);
			return idCache.cache(PCloudNodeFactory.from(target.getParent(), relocationResult));
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

		if (file.getParent().getId() == null) {
			throw new NoSuchCloudFileException(String.format("The parent folder of %s doesn't have a folderId. The file would remain in root folder", file.getPath()));
		}

		progressAware.onProgress(Progress.started(UploadState.upload(file)));
		UploadOptions uploadOptions = UploadOptions.DEFAULT;
		if (file.getId() != null && replace) {
			uploadOptions = UploadOptions.OVERRIDE_FILE;
		}

		RemoteFile uploadedFile = uploadFile(file, data, progressAware, uploadOptions, size);

		progressAware.onProgress(Progress.completed(UploadState.upload(file)));

		return idCache.cache(PCloudNodeFactory.file(file.getParent(), uploadedFile));
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

		Long parentFolderId = file.getParent().getId();
		if (parentFolderId == null) {
			parentFolderId = idCache.get(file.getParent().getPath()).getId();
		}

		String filename = file.getName();
		String encodedFilename = URLEncoder.encode(filename, UTF_8);

		try {
			RemoteFile newFile = client() //
					.createFile(parentFolderId, encodedFilename, pCloudDataSource, new Date(), listener, uploadOptions) //
					.execute();
			if (!filename.equals(encodedFilename)) {
				return client().renameFile(newFile.fileId(), filename).execute();
			}
			return newFile;
		} catch (ApiError ex) {
			handleApiError(ex);
			throw new FatalBackendException(ex);
		}
	}

	public void read(PCloudFile file, OutputStream data, final ProgressAware<DownloadState> progressAware) throws IOException, BackendException {
		progressAware.onProgress(Progress.started(DownloadState.download(file)));

		Long fileId = file.getId();
		if (fileId == null) {
			fileId = idCache.get(file.getPath()).getId();
		}

		try {
			FileLink fileLink = client().createFileLink(fileId, DownloadOptions.DEFAULT).execute();

			ProgressListener listener = (done, total) -> progressAware.onProgress( //
					progress(DownloadState.download(file)) //
							.between(0) //
							.and(file.getSize().orElse(Long.MAX_VALUE)) //
							.withValue(done));

			DataSink sink = new DataSink() {
				@Override
				public void readAll(BufferedSource source) throws IOException {
					CopyStream.copyStreamToStream(source.inputStream(), data);
				}
			};

			client().download(fileLink, sink, listener).execute();

			progressAware.onProgress(Progress.completed(DownloadState.download(file)));
		} catch(ApiError ex) {
			handleApiError(ex);
		}
	}

	public void delete(PCloudNode node) throws IOException, BackendException {
		try {
			if (node instanceof PCloudFolder) {
				client() //
						.deleteFolder(node.getId(), true).execute();
			} else {
				client() //
						.deleteFile(node.getId()).execute();
			}
			idCache.remove(node);
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
