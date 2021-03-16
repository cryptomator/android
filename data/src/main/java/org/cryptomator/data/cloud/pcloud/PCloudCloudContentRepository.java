package org.cryptomator.data.cloud.pcloud;

import android.content.Context;

import com.pcloud.sdk.ApiError;

import org.cryptomator.data.cloud.InterceptingCloudContentRepository;
import org.cryptomator.domain.PCloudCloud;
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

class PCloudCloudContentRepository extends InterceptingCloudContentRepository<PCloudCloud, PCloudNode, PCloudFolder, PCloudFile> {

	private final PCloudCloud cloud;

	public PCloudCloudContentRepository(PCloudCloud cloud, Context context, PCloudIdCache idCache) {
		super(new Intercepted(cloud, context, idCache));
		this.cloud = cloud;
	}

	@Override
	protected void throwWrappedIfRequired(Exception e) throws BackendException {
		throwConnectionErrorIfRequired(e);
		throwWrongCredentialsExceptionIfRequired(e);
	}

	private void throwConnectionErrorIfRequired(Exception e) throws NetworkConnectionException {
		if (contains(e, IOException.class)) {
			throw new NetworkConnectionException(e);
		}
	}

	private void throwWrongCredentialsExceptionIfRequired(Exception e) {
		if (e instanceof ApiError && ((ApiError) e).errorCode() == PCloudApiErrorCodes.INVALID_ACCESS_TOKEN.getValue()) {
			throw new WrongCredentialsException(cloud);
		}
	}

	private static class Intercepted implements CloudContentRepository<PCloudCloud, PCloudNode, PCloudFolder, PCloudFile> {

		private final PCloudImpl cloud;

		public Intercepted(PCloudCloud cloud, Context context, PCloudIdCache idCache) {
			this.cloud = new PCloudImpl(cloud, context, idCache);
		}

		public PCloudFolder root(PCloudCloud cloud) {
			return this.cloud.root();
		}

		@Override
		public PCloudFolder resolve(PCloudCloud cloud, String path) {
			return this.cloud.resolve(path);
		}

		@Override
		public PCloudFile file(PCloudFolder parent, String name) {
			return cloud.file(parent, name);
		}

		@Override
		public PCloudFile file(PCloudFolder parent, String name, Optional<Long> size) throws BackendException {
			return cloud.file(parent, name, size);
		}

		@Override
		public PCloudFolder folder(PCloudFolder parent, String name) {
			return cloud.folder(parent, name);
		}

		@Override
		public boolean exists(PCloudNode node) throws BackendException {
			try {
				return cloud.exists(node);
			} catch (ApiError|IOException e) {
				throw new FatalBackendException(e);
			}
		}

		@Override
		public List<PCloudNode> list(PCloudFolder folder) throws BackendException {
			try {
				return cloud.list(folder);
			} catch (ApiError | IOException e) {
				if (e instanceof ApiError) {
					if (((ApiError) e).errorCode() == PCloudApiErrorCodes.DIRECTORY_DOES_NOT_EXIST.getValue()) {
						throw new NoSuchCloudFileException();
					}
				}
				throw new FatalBackendException(e);
			}
		}

		@Override
		public PCloudFolder create(PCloudFolder folder) throws BackendException {
			try {
				return cloud.create(folder);
			} catch (ApiError | IOException e) {
				if (e instanceof ApiError) {
					if (((ApiError) e).errorCode() == PCloudApiErrorCodes.FILE_OR_FOLDER_ALREADY_EXISTS.getValue())
					throw new CloudNodeAlreadyExistsException(folder.getName());
				}
				throw new FatalBackendException(e);
			}
		}

		@Override
		public PCloudFolder move(PCloudFolder source, PCloudFolder target) throws BackendException {
			try {
				return (PCloudFolder) cloud.move(source, target);
			} catch (ApiError | IOException e) {
				if (e instanceof ApiError) {
					if (((ApiError)e).errorCode() == PCloudApiErrorCodes.DIRECTORY_DOES_NOT_EXIST.getValue()) {
						throw new NoSuchCloudFileException(source.getName());
					} else if (((ApiError)e).errorCode() == PCloudApiErrorCodes.FILE_OR_FOLDER_ALREADY_EXISTS.getValue()) {
						throw new CloudNodeAlreadyExistsException(target.getName());
					}
					throw new CloudNodeAlreadyExistsException(target.getName());
				}
				throw new FatalBackendException(e);
			}
		}

		@Override
		public PCloudFile move(PCloudFile source, PCloudFile target) throws BackendException {
			try {
				return (PCloudFile) cloud.move(source, target);
			} catch (ApiError | IOException e) {
				if (e instanceof ApiError) {
					if (((ApiError)e).errorCode() == PCloudApiErrorCodes.FILE_NOT_FOUND.getValue()) {
						throw new NoSuchCloudFileException(source.getName());
					} else if (((ApiError)e).errorCode() == PCloudApiErrorCodes.FILE_OR_FOLDER_ALREADY_EXISTS.getValue()) {
						throw new CloudNodeAlreadyExistsException(target.getName());
					}
					throw new CloudNodeAlreadyExistsException(target.getName());
				}
				throw new FatalBackendException(e);
			}
		}

		@Override
		public PCloudFile write(PCloudFile uploadFile, DataSource data, ProgressAware<UploadState> progressAware, boolean replace, long size) throws BackendException {
			try {
				return cloud.write(uploadFile, data, progressAware, replace, size);
			} catch (ApiError | IOException e) {
				if (e instanceof ApiError && ((ApiError)e).errorCode() == PCloudApiErrorCodes.FILE_NOT_FOUND.getValue()) {
					throw new NoSuchCloudFileException(uploadFile.getName());
				}
				throw new FatalBackendException(e);
			}
		}

		@Override
		public void read(PCloudFile file, Optional<File> encryptedTmpFile, OutputStream data, ProgressAware<DownloadState> progressAware) throws BackendException {
			try {
				cloud.read(file, data, progressAware);
			} catch (ApiError | IOException e) {
				if (e instanceof ApiError && ((ApiError)e).errorCode() == PCloudApiErrorCodes.FILE_NOT_FOUND.getValue()) {
					throw new NoSuchCloudFileException(file.getName());
				}
				throw new FatalBackendException(e);
			}
		}

		@Override
		public void delete(PCloudNode node) throws BackendException {
			try {
				cloud.delete(node);
			} catch (ApiError | IOException e) {
				if (e instanceof ApiError && ((ApiError)e).errorCode() == PCloudApiErrorCodes.FILE_NOT_FOUND.getValue()) {
					throw new NoSuchCloudFileException(node.getName());
				}
				throw new FatalBackendException(e);
			}
		}

		@Override
		public String checkAuthenticationAndRetrieveCurrentAccount(PCloudCloud cloud) throws BackendException {
			try {
				return this.cloud.currentAccount();
			} catch (ApiError | IOException e) {
				throw new FatalBackendException(e);
			}
		}

		@Override
		public void logout(PCloudCloud cloud) throws BackendException {
			// empty
		}
	}

}
