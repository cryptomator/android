package org.cryptomator.data.cloud.local.file;

import android.content.Context;

import org.cryptomator.domain.CloudNode;
import org.cryptomator.domain.LocalStorageCloud;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.FatalBackendException;
import org.cryptomator.domain.exception.NoSuchCloudFileException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.domain.usecases.ProgressAware;
import org.cryptomator.domain.usecases.cloud.DataSource;
import org.cryptomator.domain.usecases.cloud.DownloadState;
import org.cryptomator.domain.usecases.cloud.UploadState;
import org.cryptomator.util.Optional;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import static org.cryptomator.util.ExceptionUtil.contains;

public class LocalStorageContentRepository implements CloudContentRepository<LocalStorageCloud, LocalNode, LocalFolder, LocalFile> {

	private final LocalStorageImpl localStorageImpl;

	public LocalStorageContentRepository(Context context, LocalStorageCloud localStorageCloud) {
		this.localStorageImpl = new LocalStorageImpl(context, localStorageCloud);
	}

	@Override
	public LocalFolder root(LocalStorageCloud cloud) throws BackendException {
		return localStorageImpl.root();
	}

	@Override
	public LocalFolder resolve(LocalStorageCloud cloud, String path) throws BackendException {
		return localStorageImpl.resolve(path);
	}

	@Override
	public LocalFile file(LocalFolder parent, String name) throws BackendException {
		return localStorageImpl.file(parent, name);
	}

	@Override
	public LocalFile file(LocalFolder parent, String name, Optional<Long> size) throws BackendException {
		return localStorageImpl.file(parent, name, size);
	}

	@Override
	public LocalFolder folder(LocalFolder parent, String name) throws BackendException {
		return localStorageImpl.folder(parent, name);
	}

	@Override
	public boolean exists(LocalNode node) throws BackendException {
		return localStorageImpl.exists(node);
	}

	@Override
	public List<CloudNode> list(LocalFolder folder) throws BackendException {
		return localStorageImpl.list(folder);
	}

	@Override
	public LocalFolder create(LocalFolder folder) throws BackendException {
		return localStorageImpl.create(folder);
	}

	@Override
	public LocalFolder move(LocalFolder source, LocalFolder target) throws BackendException {
		return (LocalFolder) localStorageImpl.move(source, target);
	}

	@Override
	public LocalFile move(LocalFile source, LocalFile target) throws BackendException {
		return (LocalFile) localStorageImpl.move(source, target);
	}

	@Override
	public LocalFile write(LocalFile file, DataSource data, ProgressAware<UploadState> progressAware, boolean replace, long size) throws BackendException {
		try {
			return localStorageImpl.write(file, data, progressAware, replace, size);
		} catch (IOException e) {
			if (contains(e, FileNotFoundException.class)) {
				throw new NoSuchCloudFileException(file.getName());
			}
			throw new FatalBackendException(e);
		}
	}

	@Override
	public void read(LocalFile file, Optional<File> tmpEncryptedFile, OutputStream data, ProgressAware<DownloadState> progressAware) throws BackendException {
		try {
			localStorageImpl.read(file, data, progressAware);
		} catch (IOException e) {
			if (contains(e, FileNotFoundException.class)) {
				throw new NoSuchCloudFileException(file.getName());
			}
			throw new FatalBackendException(e);
		}
	}

	@Override
	public void delete(LocalNode node) throws BackendException {
		localStorageImpl.delete(node);
	}

	@Override
	public String checkAuthenticationAndRetrieveCurrentAccount(LocalStorageCloud cloud) throws BackendException {
		return null;
	}

	@Override
	public void logout(LocalStorageCloud cloud) throws BackendException {
		// empty
	}
}
