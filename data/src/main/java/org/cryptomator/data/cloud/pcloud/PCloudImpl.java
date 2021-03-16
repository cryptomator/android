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
import org.cryptomator.domain.CloudFile;
import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.CloudNode;
import org.cryptomator.domain.PCloudCloud;
import org.cryptomator.domain.exception.CloudNodeAlreadyExistsException;
import org.cryptomator.domain.exception.authentication.NoAuthenticationProvidedException;
import org.cryptomator.domain.usecases.ProgressAware;
import org.cryptomator.domain.usecases.cloud.DataSource;
import org.cryptomator.domain.usecases.cloud.DownloadState;
import org.cryptomator.domain.usecases.cloud.Progress;
import org.cryptomator.domain.usecases.cloud.UploadState;
import org.cryptomator.util.Optional;
import org.cryptomator.util.crypto.CredentialCryptor;

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
	private final PCloudClientFactory clientFactory = new PCloudClientFactory();
	private final PCloudCloud cloud;
	private final RootPCloudFolder root;
	private final Context context;

	PCloudImpl(PCloudCloud cloud, Context context) {
		if (cloud.accessToken() == null) {
			throw new NoAuthenticationProvidedException(cloud);
		}
		this.cloud = cloud;
		this.root = new RootPCloudFolder(cloud);
		this.context = context;
	}

	private ApiClient client() {
		return clientFactory.getClient(decrypt(cloud.accessToken()), cloud.url(), context);
	}

	private String decrypt(String password) {
		return CredentialCryptor //
				.getInstance(context) //
				.decrypt(password);
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

	public PCloudFile file(CloudFolder folder, String name) {
		return file(folder, name, Optional.empty());
	}

	public PCloudFile file(CloudFolder folder, String name, Optional<Long> size) {
		return PCloudCloudNodeFactory.file( //
				(PCloudFolder) folder, //
				name, //
				size, //
				folder.getPath() + '/' + name);
	}

	public PCloudFolder folder(CloudFolder folder, String name) {
		return PCloudCloudNodeFactory.folder( //
				(PCloudFolder) folder, //
				name, //
				folder.getPath() + '/' + name);
	}

	public boolean exists(CloudNode node) throws ApiError, IOException {
		try {
			if (node instanceof PCloudFolder) {
				client().listFolder(((PCloudFolder) node).getPath()).execute();
				return true;
			} else {
				client().stat(((PCloudFile)node).getPath()).execute();
				return true;
			}
		} catch (ApiError e) {
			if (e.errorCode() == PCloudApiErrorCodes.DIRECTORY_DOES_NOT_EXIST.getValue()
					|| e.errorCode() == PCloudApiErrorCodes.INVALID_FILE_OR_FOLDER_NAME.getValue()
					|| e.errorCode() == PCloudApiErrorCodes.FILE_OR_FOLDER_NOT_FOUND.getValue()) {
				return false;
			}
			throw e;
		}
	}

	public List<PCloudNode> list(CloudFolder folder) throws ApiError, IOException {
		List<PCloudNode> result = new ArrayList<>();
		RemoteFolder listFolderResult = client() //
				.listFolder(((PCloudFolder) folder).getId()) //
				.execute();
		List<RemoteEntry> entryMetadata = listFolderResult.children();
		for (RemoteEntry metadata : entryMetadata) {
			result.add(PCloudCloudNodeFactory.from( //
					(PCloudFolder) folder, //
					metadata));
		}
		return result;
	}

	public PCloudFolder create(CloudFolder folder) throws ApiError, IOException  {
		RemoteFolder createFolderResult = client() //
				.createFolder(((PCloudFolder)folder.getParent()).getId(), folder.getName()) //
				.execute();

		return PCloudCloudNodeFactory.from( //
				(PCloudFolder) folder.getParent(), //
				createFolderResult.asFolder());
	}

	public CloudNode move(CloudNode source, CloudNode target) throws ApiError, IOException {
		RemoteEntry relocationResult;
		if (source instanceof PCloudFolder) {
			relocationResult = client().moveFolder(((PCloudFolder) source).getId(), ((PCloudFolder) target).getId()).execute();
		} else {
			relocationResult = client().moveFile(((PCloudFile) source).getId(), ((PCloudFolder) target).getId()).execute();
		}

		return PCloudCloudNodeFactory.from( //
				(PCloudFolder) target.getParent(), //
				relocationResult);
	}

	public PCloudFile write(PCloudFile file, DataSource data, final ProgressAware<UploadState> progressAware, boolean replace, long size) throws ApiError, IOException, CloudNodeAlreadyExistsException {
		if (exists(file) && !replace) {
			throw new CloudNodeAlreadyExistsException("CloudNode already exists and replace is false");
		}

		progressAware.onProgress(Progress.started(UploadState.upload(file)));
		UploadOptions uploadOptions = UploadOptions.DEFAULT;
		if (replace) {
			uploadOptions = UploadOptions.OVERRIDE_FILE;
		}

		RemoteFile uploadedFile = uploadFile(file, data, progressAware, uploadOptions, size);

		progressAware.onProgress(Progress.completed(UploadState.upload(file)));

		return PCloudCloudNodeFactory.from( //
				file.getParent(), //
				uploadedFile);
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

		return client() //
				.createFile(file.getParent().getId(), file.getName(), pCloudDataSource, new Date(), listener, uploadOptions) //
				.execute();
	}

	public void read(CloudFile file, OutputStream data, final ProgressAware<DownloadState> progressAware) throws ApiError, IOException {
		progressAware.onProgress(Progress.started(DownloadState.download(file)));

		Long fileId = ((PCloudFile)file).getId();
		if (fileId == null) {
			fileId = client().stat(file.getPath()).execute().fileId();
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

	public void delete(CloudNode node) throws ApiError, IOException {
		if (node instanceof PCloudFolder) {
			client() //
					.deleteFolder(((PCloudFolder) node).getId()).execute();
		} else {
			client() //
					.deleteFile(((PCloudFile) node).getId()).execute();
		}

	}

	public String currentAccount() throws ApiError, IOException {
		UserInfo currentAccount = client() //
				.getUserInfo() //
				.execute();
		return currentAccount.email();
	}
}
