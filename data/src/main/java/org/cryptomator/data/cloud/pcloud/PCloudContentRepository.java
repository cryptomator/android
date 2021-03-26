package org.cryptomator.data.cloud.pcloud;

import android.content.Context;

import com.pcloud.sdk.ApiError;

import org.cryptomator.data.cloud.InterceptingCloudContentRepository;
import org.cryptomator.domain.PCloud;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.FatalBackendException;
import org.cryptomator.domain.exception.NetworkConnectionException;
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

class PCloudContentRepository extends InterceptingCloudContentRepository<PCloud, PCloudNode, PCloudFolder, PCloudFile> {

	private final PCloud cloud;

	public PCloudContentRepository(PCloud cloud, Context context) {
		super(new Intercepted(cloud, context));
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
		if (e instanceof ApiError) {
			int errorCode = ((ApiError) e).errorCode();
			if (errorCode == PCloudApiError.PCloudApiErrorCodes.INVALID_ACCESS_TOKEN.getValue() //
					|| errorCode == PCloudApiError.PCloudApiErrorCodes.ACCESS_TOKEN_REVOKED.getValue()) {
				throw new WrongCredentialsException(cloud);
			}
		}
	}

	private static class Intercepted implements CloudContentRepository<PCloud, PCloudNode, PCloudFolder, PCloudFile> {

		private final PCloudImpl cloud;

		public Intercepted(PCloud cloud, Context context) {
			this.cloud = new PCloudImpl(context, cloud);
		}

		public PCloudFolder root(PCloud cloud) {
			return this.cloud.root();
		}

		@Override
		public PCloudFolder resolve(PCloud cloud, String path) throws BackendException {
			try {
				return this.cloud.resolve(path);
			} catch (IOException ex) {
				throw new FatalBackendException(ex);
			}
		}

		@Override
		public PCloudFile file(PCloudFolder parent, String name) throws BackendException {
			try {
				return cloud.file(parent, name);
			} catch (IOException ex) {
				throw new FatalBackendException(ex);
			}
		}

		@Override
		public PCloudFile file(PCloudFolder parent, String name, Optional<Long> size) throws BackendException {
			try {
				return cloud.file(parent, name, size);
			} catch (IOException ex) {
				throw new FatalBackendException(ex);
			}
		}

		@Override
		public PCloudFolder folder(PCloudFolder parent, String name) throws BackendException {
			try {
				return cloud.folder(parent, name);
			} catch (IOException ex) {
				throw new FatalBackendException(ex);
			}
		}

		@Override
		public boolean exists(PCloudNode node) throws BackendException {
			try {
				return cloud.exists(node);
			} catch (IOException e) {
				throw new FatalBackendException(e);
			}
		}

		@Override
		public List<PCloudNode> list(PCloudFolder folder) throws BackendException {
			try {
				return cloud.list(folder);
			} catch (IOException e) {
				throw new FatalBackendException(e);
			}
		}

		@Override
		public PCloudFolder create(PCloudFolder folder) throws BackendException {
			try {
				return cloud.create(folder);
			} catch (IOException e) {
				throw new FatalBackendException(e);
			}
		}

		@Override
		public PCloudFolder move(PCloudFolder source, PCloudFolder target) throws BackendException {
			try {
				return (PCloudFolder) cloud.move(source, target);
			} catch (IOException e) {
				throw new FatalBackendException(e);
			}
		}

		@Override
		public PCloudFile move(PCloudFile source, PCloudFile target) throws BackendException {
			try {
				return (PCloudFile) cloud.move(source, target);
			} catch (IOException e) {
				throw new FatalBackendException(e);
			}
		}

		@Override
		public PCloudFile write(PCloudFile uploadFile, DataSource data, ProgressAware<UploadState> progressAware, boolean replace, long size) throws BackendException {
			try {
				return cloud.write(uploadFile, data, progressAware, replace, size);
			} catch (IOException e) {
				throw new FatalBackendException(e);
			}
		}

		@Override
		public void read(PCloudFile file, Optional<File> encryptedTmpFile, OutputStream data, ProgressAware<DownloadState> progressAware) throws BackendException {
			try {
				cloud.read(file, encryptedTmpFile, data, progressAware);
			} catch (IOException e) {
				throw new FatalBackendException(e);
			}
		}

		@Override
		public void delete(PCloudNode node) throws BackendException {
			try {
				cloud.delete(node);
			} catch (IOException e) {
				throw new FatalBackendException(e);
			}
		}

		@Override
		public String checkAuthenticationAndRetrieveCurrentAccount(PCloud cloud) throws BackendException {
			try {
				return this.cloud.currentAccount();
			} catch (IOException e) {
				throw new FatalBackendException(e);
			}
		}

		@Override
		public void logout(PCloud cloud) throws BackendException {
			// empty
		}
	}

}
