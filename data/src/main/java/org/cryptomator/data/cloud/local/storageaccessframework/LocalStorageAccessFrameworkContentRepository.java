package org.cryptomator.data.cloud.local.storageaccessframework;

import android.content.Context;
import android.os.Build;

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
import org.cryptomator.util.file.MimeTypes;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import androidx.annotation.RequiresApi;

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
public class LocalStorageAccessFrameworkContentRepository implements CloudContentRepository<LocalStorageCloud, LocalStorageAccessNode, LocalStorageAccessFolder, LocalStorageAccessFile> {

	private final LocalStorageAccessFrameworkImpl localStorageAccessFramework;

	public LocalStorageAccessFrameworkContentRepository(Context context, MimeTypes mimeTypes, LocalStorageCloud localStorageCloud) {
		this.localStorageAccessFramework = new LocalStorageAccessFrameworkImpl(context, mimeTypes, localStorageCloud, new DocumentIdCache());
	}

	@Override
	public LocalStorageAccessFolder root(LocalStorageCloud cloud) throws BackendException {
		return localStorageAccessFramework.root();
	}

	@Override
	public LocalStorageAccessFolder resolve(LocalStorageCloud cloud, String path) throws BackendException {
		return localStorageAccessFramework.resolve(path);
	}

	@Override
	public LocalStorageAccessFile file(LocalStorageAccessFolder parent, String name) throws BackendException {
		return localStorageAccessFramework.file(parent, name);
	}

	@Override
	public LocalStorageAccessFile file(LocalStorageAccessFolder parent, String name, Optional<Long> size) throws BackendException {
		return localStorageAccessFramework.file(parent, name, size);
	}

	@Override
	public LocalStorageAccessFolder folder(LocalStorageAccessFolder parent, String name) throws BackendException {
		return localStorageAccessFramework.folder(parent, name);
	}

	@Override
	public boolean exists(LocalStorageAccessNode node) throws BackendException {
		return localStorageAccessFramework.exists(node);
	}

	@Override
	public List<CloudNode> list(LocalStorageAccessFolder folder) throws BackendException {
		return localStorageAccessFramework.list(folder);
	}

	@Override
	public LocalStorageAccessFolder create(LocalStorageAccessFolder folder) throws BackendException {
		return localStorageAccessFramework.create(folder);
	}

	@Override
	public LocalStorageAccessFolder move(LocalStorageAccessFolder source, LocalStorageAccessFolder target) throws BackendException {
		if (source.getDocumentId() == null) {
			throw new NoSuchCloudFileException(source.getName());
		}
		return (LocalStorageAccessFolder) localStorageAccessFramework.move(source, target);
	}

	@Override
	public LocalStorageAccessFile move(LocalStorageAccessFile source, LocalStorageAccessFile target) throws BackendException {
		return (LocalStorageAccessFile) localStorageAccessFramework.move(source, target);
	}

	@Override
	public LocalStorageAccessFile write(LocalStorageAccessFile file, DataSource data, ProgressAware<UploadState> progressAware, boolean replace, long size) throws BackendException {
		try {
			return localStorageAccessFramework.write(file, data, progressAware, replace, size);
		} catch (IOException e) {
			throw new FatalBackendException(e);
		}
	}

	@Override
	public void read(LocalStorageAccessFile file, Optional<File> tmpEnctypted, OutputStream data, ProgressAware<DownloadState> progressAware) throws BackendException {
		try {
			if (file.getDocumentId() == null) {
				throw new NoSuchCloudFileException(file.getName());
			}
			localStorageAccessFramework.read(file, data, progressAware);
		} catch (IOException e) {
			throw new FatalBackendException(e);
		}
	}

	@Override
	public void delete(LocalStorageAccessNode node) throws BackendException {
		localStorageAccessFramework.delete(node);
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
