package org.cryptomator.data.cloud;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.CloudFile;
import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.CloudNode;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.domain.usecases.ProgressAware;
import org.cryptomator.domain.usecases.cloud.DataSource;
import org.cryptomator.domain.usecases.cloud.DownloadState;
import org.cryptomator.domain.usecases.cloud.UploadState;
import org.cryptomator.util.Optional;

import java.io.File;
import java.io.OutputStream;
import java.util.List;

public abstract class InterceptingCloudContentRepository<CloudType extends Cloud, NodeType extends CloudNode, DirType extends CloudFolder, FileType extends CloudFile>
		implements CloudContentRepository<CloudType, NodeType, DirType, FileType> {

	private final CloudContentRepository<CloudType, NodeType, DirType, FileType> delegate;

	protected InterceptingCloudContentRepository(CloudContentRepository<CloudType, NodeType, DirType, FileType> delegate) {
		this.delegate = delegate;
	}

	protected abstract void throwWrappedIfRequired(Exception e) throws BackendException;

	@Override
	public DirType root(CloudType cloud) throws BackendException {
		try {
			return delegate.root(cloud);
		} catch (BackendException e) {
			throwWrappedIfRequired(e);
			throw e;
		} catch (RuntimeException e) {
			throwWrappedIfRequired(e);
			throw e;
		}
	}

	@Override
	public DirType resolve(CloudType cloud, String path) throws BackendException {
		try {
			return delegate.resolve(cloud, path);
		} catch (BackendException e) {
			throwWrappedIfRequired(e);
			throw e;
		} catch (RuntimeException e) {
			throwWrappedIfRequired(e);
			throw e;
		}
	}

	@Override
	public FileType file(DirType parent, String name) throws BackendException {
		try {
			return delegate.file(parent, name);
		} catch (BackendException e) {
			throwWrappedIfRequired(e);
			throw e;
		} catch (RuntimeException e) {
			throwWrappedIfRequired(e);
			throw e;
		}
	}

	@Override
	public FileType file(DirType parent, String name, Optional<Long> size) throws BackendException {
		try {
			return delegate.file(parent, name, size);
		} catch (BackendException e) {
			throwWrappedIfRequired(e);
			throw e;
		} catch (RuntimeException e) {
			throwWrappedIfRequired(e);
			throw e;
		}
	}

	@Override
	public DirType folder(DirType parent, String name) throws BackendException {
		try {
			return delegate.folder(parent, name);
		} catch (BackendException e) {
			throwWrappedIfRequired(e);
			throw e;
		} catch (RuntimeException e) {
			throwWrappedIfRequired(e);
			throw e;
		}
	}

	@Override
	public boolean exists(NodeType node) throws BackendException {
		try {
			return delegate.exists(node);
		} catch (BackendException e) {
			throwWrappedIfRequired(e);
			throw e;
		} catch (RuntimeException e) {
			throwWrappedIfRequired(e);
			throw e;
		}
	}

	@Override
	public List<? extends CloudNode> list(DirType folder) throws BackendException {
		try {
			return delegate.list(folder);
		} catch (BackendException e) {
			throwWrappedIfRequired(e);
			throw e;
		} catch (RuntimeException e) {
			throwWrappedIfRequired(e);
			throw e;
		}
	}

	@Override
	public DirType create(DirType folder) throws BackendException {
		try {
			return delegate.create(folder);
		} catch (BackendException e) {
			throwWrappedIfRequired(e);
			throw e;
		} catch (RuntimeException e) {
			throwWrappedIfRequired(e);
			throw e;
		}
	}

	@Override
	public DirType move(DirType source, DirType target) throws BackendException {
		try {
			return delegate.move(source, target);
		} catch (BackendException e) {
			throwWrappedIfRequired(e);
			throw e;
		} catch (RuntimeException e) {
			throwWrappedIfRequired(e);
			throw e;
		}
	}

	@Override
	public FileType move(FileType source, FileType target) throws BackendException {
		try {
			return delegate.move(source, target);
		} catch (BackendException e) {
			throwWrappedIfRequired(e);
			throw e;
		} catch (RuntimeException e) {
			throwWrappedIfRequired(e);
			throw e;
		}
	}

	@Override
	public FileType write(FileType file, DataSource data, ProgressAware<UploadState> progressAware, boolean replace, long size) throws BackendException {
		try {
			return delegate.write(file, data, progressAware, replace, size);
		} catch (BackendException e) {
			throwWrappedIfRequired(e);
			throw e;
		} catch (RuntimeException e) {
			throwWrappedIfRequired(e);
			throw e;
		}
	}

	@Override
	public void read(FileType file, Optional<File> encryptedTmpFile, OutputStream data, ProgressAware<DownloadState> progressAware) throws BackendException {
		try {
			delegate.read(file, encryptedTmpFile, data, progressAware);
		} catch (BackendException e) {
			throwWrappedIfRequired(e);
			throw e;
		} catch (RuntimeException e) {
			throwWrappedIfRequired(e);
			throw e;
		}
	}

	@Override
	public void delete(NodeType node) throws BackendException {
		try {
			delegate.delete(node);
		} catch (BackendException e) {
			throwWrappedIfRequired(e);
			throw e;
		} catch (RuntimeException e) {
			throwWrappedIfRequired(e);
			throw e;
		}
	}

	@Override
	public String checkAuthenticationAndRetrieveCurrentAccount(CloudType cloud) throws BackendException {
		try {
			return delegate.checkAuthenticationAndRetrieveCurrentAccount(cloud);
		} catch (BackendException e) {
			throwWrappedIfRequired(e);
			throw e;
		} catch (RuntimeException e) {
			throwWrappedIfRequired(e);
			throw e;
		}
	}

	@Override
	public void logout(CloudType cloud) throws BackendException {
		try {
			delegate.logout(cloud);
		} catch (BackendException e) {
			throwWrappedIfRequired(e);
			throw e;
		} catch (RuntimeException e) {
			throwWrappedIfRequired(e);
			throw e;
		}
	}
}
