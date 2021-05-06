package org.cryptomator.data.cloud.webdav;

import android.content.Context;

import org.cryptomator.data.cloud.webdav.network.ConnectionHandlerHandlerImpl;
import org.cryptomator.data.util.CopyStream;
import org.cryptomator.data.util.TransferredBytesAwareInputStream;
import org.cryptomator.data.util.TransferredBytesAwareOutputStream;
import org.cryptomator.domain.CloudFile;
import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.CloudNode;
import org.cryptomator.domain.WebDavCloud;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.CloudNodeAlreadyExistsException;
import org.cryptomator.domain.exception.FatalBackendException;
import org.cryptomator.domain.exception.NotFoundException;
import org.cryptomator.domain.exception.ParentFolderDoesNotExistException;
import org.cryptomator.domain.usecases.ProgressAware;
import org.cryptomator.domain.usecases.cloud.DataSource;
import org.cryptomator.domain.usecases.cloud.DownloadState;
import org.cryptomator.domain.usecases.cloud.Progress;
import org.cryptomator.domain.usecases.cloud.UploadState;
import org.cryptomator.util.Optional;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import okhttp3.HttpUrl;

import static org.cryptomator.domain.usecases.cloud.Progress.progress;

class WebDavImpl {

	private final WebDavCloud cloud;
	private final HttpUrl baseUrl;
	private final RootWebDavFolder root;
	private final ConnectionHandlerHandlerImpl connectionHandler;

	WebDavImpl(WebDavCloud cloud, ConnectionHandlerHandlerImpl connectionHandler) {
		this.cloud = cloud;
		this.baseUrl = HttpUrl.parse(cloud.url());
		this.root = new RootWebDavFolder(cloud);
		this.connectionHandler = connectionHandler;
	}

	public WebDavFolder root() {
		return root;
	}

	public WebDavFolder resolve(String path) {
		if (path.startsWith("/")) {
			path = path.substring(1);
		}
		String[] names = path.split("/");
		WebDavFolder folder = root;
		for (String name : names) {
			folder = folder(folder, name);
		}
		return folder;
	}

	public WebDavFile file(CloudFolder parent, String name) {
		return file(parent, name, Optional.empty());
	}

	public WebDavFile file(CloudFolder parent, String name, Optional<Long> size) {
		return new WebDavFile((WebDavFolder) parent, name, parent.getPath() + '/' + name, size, Optional.empty());
	}

	public WebDavFolder folder(CloudFolder parent, String name) {
		return new WebDavFolder((WebDavFolder) parent, name, parent.getPath() + '/' + name);
	}

	public boolean exists(CloudNode node) throws BackendException {
		try {
			return connectionHandler //
					.get(absoluteUriFrom(node.getPath()), //
							node.getParent()) != null;
		} catch (NotFoundException e) {
			return false;
		}
	}

	public List<CloudNode> list(WebDavFolder folder) throws BackendException {
		return connectionHandler //
				.dirList(absoluteUriFrom(folder.getPath()), //
						folder);
	}

	public WebDavFolder create(WebDavFolder folder) throws BackendException {
		try {
			return createExcludingParents(folder);
		} catch (NotFoundException | ParentFolderDoesNotExistException e) {
			create(folder.getParent());
			return createExcludingParents(folder);
		}
	}

	private WebDavFolder createExcludingParents(WebDavFolder folder) throws BackendException {
		if (folder.getParent() == null) {
			return folder;
		} else {
			return connectionHandler.createFolder( //
					absoluteUriFrom(folder.getPath()), //
					folder);
		}
	}

	public WebDavFolder move(CloudFolder source, CloudFolder target) throws BackendException {
		moveFileOrFolder(source, target);
		return new WebDavFolder( //
				(WebDavFolder) target.getParent() //
				, target.getName() //
				, target.getPath());
	}

	public WebDavFile move(CloudFile source, CloudFile target) throws BackendException {
		moveFileOrFolder(source, target);
		return new WebDavFile( //
				(WebDavFolder) target.getParent() //
				, target.getName() //
				, target.getPath() //
				, source.getSize() //
				, source.getModified());
	}

	private void moveFileOrFolder(CloudNode source, CloudNode target) throws BackendException {
		if (exists(target)) {
			throw new CloudNodeAlreadyExistsException(target.getName());
		}

		connectionHandler //
				.move(absoluteUriFrom(source.getPath()), //
						absoluteUriFrom(target.getPath()));
	}

	public WebDavFile write(final WebDavFile uploadFile, DataSource data, final ProgressAware<UploadState> progressAware, boolean replace, final long size) //
			throws BackendException, IOException {
		if (!replace && exists(uploadFile)) {
			throw new CloudNodeAlreadyExistsException("CloudNode already exists and replace is false");
		}

		progressAware.onProgress(Progress.started(UploadState.upload(uploadFile)));

		try (TransferredBytesAwareDataSource out = new TransferredBytesAwareDataSource(data) {
			@Override
			public void bytesTrasferred(long transferred) {
				progressAware.onProgress( //
						progress(UploadState.upload(uploadFile)) //
								.between(0) //
								.and(size) //
								.withValue(transferred));
			}
		}) {
			connectionHandler //
					.writeFile( //
							absoluteUriFrom(uploadFile.getPath()), out);
		}

		WebDavFile cloudFile = (WebDavFile) connectionHandler //
				.get(absoluteUriFrom(uploadFile.getPath()), //
						uploadFile.getParent());

		if (cloudFile == null) {
			throw new FatalBackendException("Unable to get CloudFile after upload.");
		}

		return cloudFile;
	}

	public void checkAuthenticationAndServerCompatibility(String url) throws BackendException {
		connectionHandler.checkAuthenticationAndServerCompatibility(url);
	}

	public void read(final CloudFile file, OutputStream data, final ProgressAware<DownloadState> progressAware) throws BackendException, IOException {
		progressAware.onProgress(Progress.started(DownloadState.download(file)));

		try (InputStream in = connectionHandler.readFile(absoluteUriFrom(file.getPath())); //
			 TransferredBytesAwareOutputStream out = new TransferredBytesAwareOutputStream(data) {
				 @Override
				 public void bytesTransferred(long transferred) {
					 progressAware.onProgress( //
							 progress(DownloadState.download(file)) //
									 .between(0) //
									 .and(file.getSize().orElse(Long.MAX_VALUE)) //
									 .withValue(transferred));
				 }
			 }) {
			CopyStream.copyStreamToStream(in, out);
		}

		progressAware.onProgress(Progress.completed(DownloadState.download(file)));
	}

	public void delete(CloudNode node) throws BackendException {
		connectionHandler.delete(absoluteUriFrom(node.getPath()));
	}

	private String absoluteUriFrom(String path) {
		path = removeLeadingSlash(path);

		return baseUrl.newBuilder() //
				.addPathSegments(path) //
				.build() //
				.toString();
	}

	private String removeLeadingSlash(String path) {
		return path.length() > 0 && path.charAt(0) == '/' ? path.substring(1) : path;
	}

	public String currentAccount() throws BackendException {
		checkAuthenticationAndServerCompatibility(cloud.url());
		return cloud.url();
	}

	private static abstract class TransferredBytesAwareDataSource implements DataSource {

		private final DataSource data;

		TransferredBytesAwareDataSource(DataSource data) {
			this.data = data;
		}

		@Override
		public Optional<Long> size(Context context) {
			return data.size(context);
		}

		@Override
		public InputStream open(Context context) throws IOException {
			return new TransferredBytesAwareInputStream(data.open(context)) {
				@Override
				public void bytesTransferred(long transferred) {
					TransferredBytesAwareDataSource.this.bytesTrasferred(transferred);
				}
			};
		}

		@Override
		public void close() throws IOException {
			data.close();
		}

		public abstract void bytesTrasferred(long transferred);

		@Override
		public DataSource decorate(DataSource delegate) {
			return delegate;
		}
	}
}
