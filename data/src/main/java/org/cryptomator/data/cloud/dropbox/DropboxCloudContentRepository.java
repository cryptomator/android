package org.cryptomator.data.cloud.dropbox;

import android.content.Context;

import com.dropbox.core.DbxException;
import com.dropbox.core.InvalidAccessTokenException;
import com.dropbox.core.NetworkIOException;
import com.dropbox.core.v2.files.CreateFolderErrorException;
import com.dropbox.core.v2.files.DeleteErrorException;
import com.dropbox.core.v2.files.DownloadErrorException;
import com.dropbox.core.v2.files.ListFolderErrorException;
import com.dropbox.core.v2.files.RelocationErrorException;

import org.cryptomator.data.cloud.InterceptingCloudContentRepository;
import org.cryptomator.domain.DropboxCloud;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.CloudNodeAlreadyExistsException;
import org.cryptomator.domain.exception.FatalBackendException;
import org.cryptomator.domain.exception.NetworkConnectionException;
import org.cryptomator.domain.exception.NoSuchCloudFileException;
import org.cryptomator.domain.exception.authentication.WrongCredentialsException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.domain.usecases.ProgressAware;
import org.cryptomator.domain.usecases.cloud.DataSource;
import org.cryptomator.domain.usecases.cloud.DownloadState;
import org.cryptomator.domain.usecases.cloud.UploadState;
import org.cryptomator.util.Optional;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import static org.cryptomator.util.ExceptionUtil.contains;
import static org.cryptomator.util.ExceptionUtil.extract;

class DropboxCloudContentRepository extends InterceptingCloudContentRepository<DropboxCloud, DropboxNode, DropboxFolder, DropboxFile> {

	private final DropboxCloud cloud;

	public DropboxCloudContentRepository(DropboxCloud cloud, Context context) {
		super(new Intercepted(cloud, context));
		this.cloud = cloud;
	}

	@Override
	protected void throwWrappedIfRequired(Exception e) throws BackendException {
		throwConnectionErrorIfRequired(e);
		throwWrongCredentialsExceptionIfRequired(e);
	}

	private void throwConnectionErrorIfRequired(Exception e) throws NetworkConnectionException {
		if (contains(e, NetworkIOException.class)) {
			throw new NetworkConnectionException(e);
		}
	}

	private void throwWrongCredentialsExceptionIfRequired(Exception e) {
		if (contains(e, InvalidAccessTokenException.class)) {
			throw new WrongCredentialsException(cloud);
		}
	}

	private static class Intercepted implements CloudContentRepository<DropboxCloud, DropboxNode, DropboxFolder, DropboxFile> {

		private final DropboxImpl cloud;

		public Intercepted(DropboxCloud cloud, Context context) {
			this.cloud = new DropboxImpl(cloud, context);
		}

		public DropboxFolder root(DropboxCloud cloud) {
			return this.cloud.root();
		}

		@Override
		public DropboxFolder resolve(DropboxCloud cloud, String path) {
			return this.cloud.resolve(path);
		}

		@Override
		public DropboxFile file(DropboxFolder parent, String name) {
			return cloud.file(parent, name);
		}

		@Override
		public DropboxFile file(DropboxFolder parent, String name, Optional<Long> size) throws BackendException {
			return cloud.file(parent, name, size);
		}

		@Override
		public DropboxFolder folder(DropboxFolder parent, String name) {
			return cloud.folder(parent, name);
		}

		@Override
		public boolean exists(DropboxNode node) throws BackendException {
			try {
				return cloud.exists(node);
			} catch (DbxException e) {
				throw new FatalBackendException(e);
			}
		}

		@Override
		public List<DropboxNode> list(DropboxFolder folder) throws BackendException {
			try {
				return cloud.list(folder);
			} catch (DbxException e) {
				if (e instanceof ListFolderErrorException) {
					if (((ListFolderErrorException) e).errorValue.getPathValue().isNotFound()) {
						throw new NoSuchCloudFileException();
					}
				}
				throw new FatalBackendException(e);
			}
		}

		@Override
		public DropboxFolder create(DropboxFolder folder) throws BackendException {
			try {
				return cloud.create(folder);
			} catch (DbxException e) {
				if (e instanceof CreateFolderErrorException) {
					throw new CloudNodeAlreadyExistsException(folder.getName());
				}
				throw new FatalBackendException(e);
			}
		}

		@Override
		public DropboxFolder move(DropboxFolder source, DropboxFolder target) throws BackendException {
			try {
				return (DropboxFolder) cloud.move(source, target);
			} catch (DbxException e) {
				if (e instanceof RelocationErrorException) {
					if (extract(e, RelocationErrorException.class).get().errorValue.isFromLookup()) {
						throw new NoSuchCloudFileException(source.getName());
					}
					throw new CloudNodeAlreadyExistsException(target.getName());
				}
				throw new FatalBackendException(e);
			}
		}

		@Override
		public DropboxFile move(DropboxFile source, DropboxFile target) throws BackendException {
			try {
				return (DropboxFile) cloud.move(source, target);
			} catch (DbxException e) {
				if (e instanceof RelocationErrorException) {
					throw new CloudNodeAlreadyExistsException(target.getName());
				}
				throw new FatalBackendException(e);
			}
		}

		@Override
		public DropboxFile write(DropboxFile uploadFile, DataSource data, ProgressAware<UploadState> progressAware, boolean replace, long size) throws BackendException {
			try {
				return cloud.write(uploadFile, data, progressAware, replace, size);
			} catch (IOException | DbxException e) {
				if (contains(e, NoSuchCloudFileException.class)) {
					throw new NoSuchCloudFileException(uploadFile.getName());
				}
				throw new FatalBackendException(e);
			}
		}

		@Override
		public void read(DropboxFile file, Optional<File> encryptedTmpFile, OutputStream data, ProgressAware<DownloadState> progressAware) throws BackendException {
			try {
				cloud.read(file, encryptedTmpFile, data, progressAware);
			} catch (IOException | DbxException e) {
				if (contains(e, DownloadErrorException.class)) {
					if (extract(e, DownloadErrorException.class).get().errorValue.getPathValue().isNotFound()) {
						throw new NoSuchCloudFileException(file.getName());
					}
				}
				throw new FatalBackendException(e);
			}
		}

		@Override
		public void delete(DropboxNode node) throws BackendException {
			try {
				cloud.delete(node);
			} catch (DbxException e) {
				if (contains(e, DeleteErrorException.class)) {
					if (extract(e, DeleteErrorException.class).get().errorValue.getPathLookupValue().isNotFound()) {
						throw new NoSuchCloudFileException(node.getName());
					}
				}
				throw new FatalBackendException(e);
			}
		}

		@Override
		public String checkAuthenticationAndRetrieveCurrentAccount(DropboxCloud cloud) throws BackendException {
			try {
				return this.cloud.currentAccount();
			} catch (DbxException e) {
				throw new FatalBackendException(e);
			}
		}

		@Override
		public void logout(DropboxCloud cloud) throws BackendException {
			// empty
		}
	}

}
