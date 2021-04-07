package org.cryptomator.data.cloud.local.file;

import android.content.Context;

import org.cryptomator.data.util.TransferredBytesAwareInputStream;
import org.cryptomator.data.util.TransferredBytesAwareOutputStream;
import org.cryptomator.domain.CloudFile;
import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.CloudNode;
import org.cryptomator.domain.LocalStorageCloud;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.CloudNodeAlreadyExistsException;
import org.cryptomator.domain.exception.FatalBackendException;
import org.cryptomator.domain.exception.NoSuchCloudFileException;
import org.cryptomator.domain.usecases.ProgressAware;
import org.cryptomator.domain.usecases.cloud.DataSource;
import org.cryptomator.domain.usecases.cloud.DownloadState;
import org.cryptomator.domain.usecases.cloud.Progress;
import org.cryptomator.domain.usecases.cloud.UploadState;
import org.cryptomator.util.Optional;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.cryptomator.data.util.CopyStream.copyStreamToStream;
import static org.cryptomator.domain.usecases.cloud.Progress.progress;

class LocalStorageImpl {

	private final Context context;
	private final RootLocalFolder root;

	LocalStorageImpl(Context context, LocalStorageCloud localStorageCloud) {
		this.context = context;
		this.root = new RootLocalFolder(localStorageCloud);
	}

	public LocalFolder root() {
		return root;
	}

	public LocalFolder resolve(String path) {
		if (path.startsWith(root.getPath())) {
			path = path.substring(root.getPath().length() + 1);
		}
		String[] names = path.split("/");
		LocalFolder folder = root;
		for (String name : names) {
			folder = folder(folder, name);
		}
		return folder;
	}

	public LocalFile file(CloudFolder folder, String name) {
		return file(folder, name, Optional.empty());
	}

	public LocalFile file(CloudFolder folder, String name, Optional<Long> size) {
		return LocalStorageNodeFactory.file( //
				(LocalFolder) folder, //
				name, //
				folder.getPath() + '/' + name, //
				size, //
				Optional.empty());
	}

	public LocalFolder folder(CloudFolder folder, String name) {
		return LocalStorageNodeFactory.folder( //
				(LocalFolder) folder, //
				name, //
				folder.getPath() + '/' + name);
	}

	public boolean exists(CloudNode node) {
		return new File(node.getPath()).exists();
	}

	public List<CloudNode> list(LocalFolder folder) throws BackendException {
		List<CloudNode> result = new ArrayList<>();
		File localDirectory = new File(folder.getPath());
		if (!exists(folder)) {
			throw new NoSuchCloudFileException();
		}
		for (File file : localDirectory.listFiles()) {
			result.add(LocalStorageNodeFactory.from(folder, file));
		}
		return result;
	}

	public LocalFolder create(LocalFolder folder) throws BackendException {
		File createFolder = new File(folder.getPath());
		if (createFolder.exists()) {
			throw new CloudNodeAlreadyExistsException(folder.getName());
		}
		if (!createFolder.mkdirs()) {
			throw new FatalBackendException("Couldn't create a local folder at " + folder.getPath());
		}

		return LocalStorageNodeFactory.folder( //
				folder.getParent(), //
				createFolder);
	}

	public LocalNode move(CloudNode source, CloudNode target) throws BackendException {
		File sourceFile = new File(source.getPath());
		File targetFile = new File(target.getPath());
		if (targetFile.exists()) {
			throw new CloudNodeAlreadyExistsException(target.getName());
		}
		if (!sourceFile.exists()) {
			throw new NoSuchCloudFileException(source.getName());
		}
		if (!sourceFile.renameTo(targetFile)) {
			throw new FatalBackendException("Couldn't move " + source.getPath() + " to " + target.getPath());
		}
		return LocalStorageNodeFactory.from((LocalFolder) target.getParent(), targetFile);
	}

	public void delete(CloudNode node) {
		File fileOrDirectory = new File(node.getPath());
		if (!deleteRecursive(fileOrDirectory)) {
			throw new FatalBackendException("Couldn't delete local CloudNode " + fileOrDirectory);
		}
	}

	private boolean deleteRecursive(File fileOrDirectory) {
		if (fileOrDirectory.isDirectory()) {
			for (File child : fileOrDirectory.listFiles()) {
				deleteRecursive(child);
			}
		}
		return fileOrDirectory.delete();
	}

	public LocalFile write(final CloudFile file, DataSource data, final ProgressAware<UploadState> progressAware, boolean replace, final long size) throws IOException, BackendException {
		if (!replace && exists(file)) {
			throw new CloudNodeAlreadyExistsException("CloudNode already exists and replace is false");
		}

		progressAware.onProgress(Progress.started(UploadState.upload(file)));
		File localFile = new File(file.getPath());

		try (OutputStream out = new FileOutputStream(localFile); TransferredBytesAwareInputStream in = new TransferredBytesAwareInputStream(data.open(context)) {
			@Override
			public void bytesTransferred(long transferred) {
				progressAware.onProgress( //
						progress(UploadState.upload(file)) //
								.between(0) //
								.and(size) //
								.withValue(transferred));
			}
		}) {
			copyStreamToStream(in, out);
		}

		progressAware.onProgress(Progress.completed(UploadState.upload(file)));

		return LocalStorageNodeFactory.file( //
				(LocalFolder) file.getParent(), //
				file.getName(), //
				localFile.getPath(), //
				Optional.of(localFile.length()), //
				Optional.of(new Date(localFile.lastModified())));
	}

	public void read(final LocalFile file, OutputStream data, final ProgressAware<DownloadState> progressAware) throws IOException {
		progressAware.onProgress(Progress.started(DownloadState.download(file)));
		File localFile = new File(file.getPath());

		try (InputStream in = new FileInputStream(localFile); TransferredBytesAwareOutputStream out = new TransferredBytesAwareOutputStream(data) {
			@Override
			public void bytesTransferred(long transferred) {
				progressAware //
						.onProgress(progress(DownloadState.download(file)) //
								.between(0) //
								.and(localFile.length()) //
								.withValue(transferred));
			}
		}) {
			copyStreamToStream(in, out);
		}

		progressAware.onProgress(Progress.completed(DownloadState.download(file)));
	}

}
