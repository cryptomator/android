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
import org.cryptomator.domain.exception.NoSuchCloudFileException;
import org.cryptomator.domain.exception.authentication.NoAuthenticationProvidedException;
import org.cryptomator.domain.usecases.ProgressAware;
import org.cryptomator.domain.usecases.cloud.DataSource;
import org.cryptomator.domain.usecases.cloud.DownloadState;
import org.cryptomator.domain.usecases.cloud.Progress;
import org.cryptomator.domain.usecases.cloud.UploadState;
import org.cryptomator.util.Optional;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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

	public PCloudFolder resolve(String path) {
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

	private Optional<RemoteEntry> findEntry(Long folderId, String name, boolean isFolder) {
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
		} catch(ApiError | IOException ex) {
			return Optional.empty();
		}
	}

	public PCloudFile file(PCloudFolder parent, String name) {
		return file(parent, name, Optional.empty());
	}

	public PCloudFile file(PCloudFolder parent, String name, Optional<Long> size) {
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

	public PCloudFolder folder(PCloudFolder parent, String name) {
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

	public boolean exists(PCloudNode node) throws ApiError, IOException {
		try {
			if (node instanceof PCloudFolder) {
				RemoteFolder remoteFolder = client().listFolder(node.getPath()).execute();
				idCache.add(PCloudNodeFactory.folder(node.getParent(), remoteFolder));
			} else {
				RemoteFile remoteFile = client().stat(node.getPath()).execute();
				idCache.add(PCloudNodeFactory.file(node.getParent(), remoteFile));
			}
			return true;
		} catch (ApiError e) {
			if (e.errorCode() == PCloudApiErrorCodes.DIRECTORY_DOES_NOT_EXIST.getValue()
					|| e.errorCode() == PCloudApiErrorCodes.COMPONENT_OF_PARENT_DIRECTORY_DOES_NOT_EXIST.getValue()
					|| e.errorCode() == PCloudApiErrorCodes.INVALID_FILE_OR_FOLDER_NAME.getValue()
					|| e.errorCode() == PCloudApiErrorCodes.FILE_OR_FOLDER_NOT_FOUND.getValue()) {
				return false;
			}
			throw e;
		}
	}

	public List<PCloudNode> list(PCloudFolder folder) throws ApiError, IOException {
		List<PCloudNode> result = new ArrayList<>();

		Long folderId = folder.getId();
		RemoteFolder listFolderResult;
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
	}

	public PCloudFolder create(PCloudFolder folder) throws ApiError, IOException  {
		if (folder.getParent().getId() == null) {
			folder = new PCloudFolder( //
					create(folder.getParent()), //
					folder.getName(), folder.getPath(), folder.getId() //
					//
			);
		}

		RemoteFolder createdFolder = client() //
				.createFolder(folder.getParent().getId(), folder.getName()) //
				.execute();
		return idCache.cache( //
				PCloudNodeFactory.folder(folder.getParent(), createdFolder));
	}

	public PCloudNode move(PCloudNode source, PCloudNode target) throws ApiError, BackendException, IOException {
		if (exists(target)) {
			throw new CloudNodeAlreadyExistsException(target.getName());
		}

		RemoteEntry relocationResult;

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
	}

	public PCloudFile write(PCloudFile file, DataSource data, final ProgressAware<UploadState> progressAware, boolean replace, long size)
			throws ApiError, BackendException, IOException {
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
			throws ApiError, IOException {
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

		return client() //
				.createFile(parentFolderId, file.getName(), pCloudDataSource, new Date(), listener, uploadOptions) //
				.execute();
	}

	public void read(PCloudFile file, OutputStream data, final ProgressAware<DownloadState> progressAware) throws ApiError, IOException {
		progressAware.onProgress(Progress.started(DownloadState.download(file)));

		Long fileId = file.getId();
		if (fileId == null) {
			fileId = idCache.get(file.getPath()).getId();
		}

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
	}

	public void delete(PCloudNode node) throws ApiError, IOException {
		if (node instanceof PCloudFolder) {
			client() //
					.deleteFolder(node.getId(), true).execute();
		} else {
			client() //
					.deleteFile(node.getId()).execute();
		}
		idCache.remove(node);
	}

	public String currentAccount() throws ApiError, IOException {
		UserInfo currentAccount = client() //
				.getUserInfo() //
				.execute();
		return currentAccount.email();
	}
}
