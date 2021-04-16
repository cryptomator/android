package org.cryptomator.data.cloud.s3;

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

class S3CloudContentRepository extends InterceptingCloudContentRepository<PCloud, S3Node, S3Folder, S3File> {

	private final PCloud cloud;

	public S3CloudContentRepository(PCloud cloud, Context context) {
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

	private static class Intercepted implements CloudContentRepository<PCloud, S3Node, S3Folder, S3File> {

		private final S3Impl cloud;

		public Intercepted(PCloud cloud, Context context) {
			this.cloud = new S3Impl(context, cloud);
		}

		public S3Folder root(PCloud cloud) {
			return this.cloud.root();
		}

		@Override
		public S3Folder resolve(PCloud cloud, String path) throws BackendException {
			try {
				return this.cloud.resolve(path);
			} catch (IOException ex) {
				throw new FatalBackendException(ex);
			}
		}

		@Override
		public S3File file(S3Folder parent, String name) throws BackendException {
			try {
				return cloud.file(parent, name);
			} catch (IOException ex) {
				throw new FatalBackendException(ex);
			}
		}

		@Override
		public S3File file(S3Folder parent, String name, Optional<Long> size) throws BackendException {
			try {
				return cloud.file(parent, name, size);
			} catch (IOException ex) {
				throw new FatalBackendException(ex);
			}
		}

		@Override
		public S3Folder folder(S3Folder parent, String name) throws BackendException {
			try {
				return cloud.folder(parent, name);
			} catch (IOException ex) {
				throw new FatalBackendException(ex);
			}
		}

		@Override
		public boolean exists(S3Node node) throws BackendException {
			try {
				return cloud.exists(node);
			} catch (IOException e) {
				throw new FatalBackendException(e);
			}
		}

		@Override
		public List<S3Node> list(S3Folder folder) throws BackendException {
			try {
				return cloud.list(folder);
			} catch (IOException e) {
				throw new FatalBackendException(e);
			}
		}

		@Override
		public S3Folder create(S3Folder folder) throws BackendException {
			try {
				return cloud.create(folder);
			} catch (IOException e) {
				throw new FatalBackendException(e);
			}
		}

		@Override
		public S3Folder move(S3Folder source, S3Folder target) throws BackendException {
			try {
				return (S3Folder) cloud.move(source, target);
			} catch (IOException e) {
				throw new FatalBackendException(e);
			}
		}

		@Override
		public S3File move(S3File source, S3File target) throws BackendException {
			try {
				return (S3File) cloud.move(source, target);
			} catch (IOException e) {
				throw new FatalBackendException(e);
			}
		}

		@Override
		public S3File write(S3File uploadFile, DataSource data, ProgressAware<UploadState> progressAware, boolean replace, long size) throws BackendException {
			try {
				return cloud.write(uploadFile, data, progressAware, replace, size);
			} catch (IOException e) {
				throw new FatalBackendException(e);
			}
		}

		@Override
		public void read(S3File file, Optional<File> encryptedTmpFile, OutputStream data, ProgressAware<DownloadState> progressAware) throws BackendException {
			try {
				cloud.read(file, encryptedTmpFile, data, progressAware);
			} catch (IOException e) {
				throw new FatalBackendException(e);
			}
		}

		@Override
		public void delete(S3Node node) throws BackendException {
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
