package org.cryptomator.data.cloud.onedrive;

import android.content.Context;

import com.microsoft.graph.core.GraphErrorCodes;

import org.cryptomator.data.cloud.InterceptingCloudContentRepository;
import org.cryptomator.data.cloud.onedrive.graph.ClientException;
import org.cryptomator.domain.CloudNode;
import org.cryptomator.domain.OnedriveCloud;
import org.cryptomator.domain.exception.BackendException;
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
import java.net.SocketTimeoutException;
import java.util.List;

import static org.cryptomator.util.ExceptionUtil.contains;

class OnedriveCloudContentRepository extends InterceptingCloudContentRepository<OnedriveCloud, OnedriveNode, OnedriveFolder, OnedriveFile> {

	private final OnedriveCloud cloud;

	public OnedriveCloudContentRepository(OnedriveCloud cloud, Context context) {
		super(new Intercepted(cloud, context));
		this.cloud = cloud;
	}

	@Override
	protected void throwWrappedIfRequired(Exception e) throws BackendException {
		throwNetworkConnectionExceptionIfRequired(e);
		throwWrongCredentialsExceptionIfRequired(e);
	}

	private void throwNetworkConnectionExceptionIfRequired(Exception e) throws NetworkConnectionException {
		if (contains(e, SocketTimeoutException.class)) {
			throw new NetworkConnectionException(e);
		}
	}

	private void throwWrongCredentialsExceptionIfRequired(Exception e) {
		if (isAuthenticationError(e)) {
			throw new WrongCredentialsException(cloud);
		}
	}

	private boolean isAuthenticationError(Throwable e) {
		return e != null //
				&& ((e instanceof ClientException && ((ClientException) e).errorCode().equals(GraphErrorCodes.AUTHENTICATION_FAILURE)) //
						|| isAuthenticationError(e.getCause()));
	}

	private static class Intercepted implements CloudContentRepository<OnedriveCloud, OnedriveNode, OnedriveFolder, OnedriveFile> {

		private final OnedriveImpl oneDriveImpl;

		public Intercepted(OnedriveCloud cloud, Context context) {
			this.oneDriveImpl = new OnedriveImpl(cloud, context, new OnedriveIdCache());
		}

		@Override
		public OnedriveFolder root(OnedriveCloud cloud) {
			return oneDriveImpl.root();
		}

		@Override
		public OnedriveFolder resolve(OnedriveCloud cloud, String path) {
			return oneDriveImpl.resolve(path);
		}

		@Override
		public OnedriveFile file(OnedriveFolder parent, String name) {
			return oneDriveImpl.file(parent, name);
		}

		@Override
		public OnedriveFile file(OnedriveFolder parent, String name, Optional<Long> size) {
			return oneDriveImpl.file(parent, name, size);
		}

		@Override
		public OnedriveFolder folder(OnedriveFolder parent, String name) {
			return oneDriveImpl.folder(parent, name);
		}

		@Override
		public boolean exists(OnedriveNode node) throws BackendException {
			return oneDriveImpl.exists(node);
		}

		@Override
		public List<CloudNode> list(OnedriveFolder folder) throws BackendException {
			return oneDriveImpl.list(folder);
		}

		@Override
		public OnedriveFolder create(OnedriveFolder folder) throws BackendException {
			return oneDriveImpl.create(folder);
		}

		@Override
		public OnedriveFolder move(OnedriveFolder source, OnedriveFolder target) throws BackendException {
			return (OnedriveFolder) oneDriveImpl.move(source, target);
		}

		@Override
		public OnedriveFile move(OnedriveFile source, OnedriveFile target) throws BackendException {
			return (OnedriveFile) oneDriveImpl.move(source, target);
		}

		@Override
		public OnedriveFile write(OnedriveFile file, DataSource data, ProgressAware<UploadState> progressAware, boolean replace, long size) throws BackendException {
			try {
				return oneDriveImpl.write(file, data, progressAware, replace, size);
			} catch (BackendException e) {
				if (contains(e, NoSuchCloudFileException.class)) {
					throw new NoSuchCloudFileException(file.getName());
				}
				throw e;
			}
		}

		@Override
		public void read(OnedriveFile file, Optional<File> tmpEncryptedFile, OutputStream data, ProgressAware<DownloadState> progressAware) throws BackendException {
			try {
				oneDriveImpl.read(file, tmpEncryptedFile, data, progressAware);
			} catch (IOException | BackendException e) {
				if (contains(e, NoSuchCloudFileException.class)) {
					throw new NoSuchCloudFileException(file.getName());
				} else if (e instanceof IOException) {
					throw new FatalBackendException(e);
				} else if (e instanceof BackendException) {
					throw (BackendException) e;
				}
			}
		}

		@Override
		public void delete(OnedriveNode node) throws BackendException {
			oneDriveImpl.delete(node);
		}

		@Override
		public String checkAuthenticationAndRetrieveCurrentAccount(OnedriveCloud cloud) throws BackendException {
			return oneDriveImpl.currentAccount();
		}

		@Override
		public void logout(OnedriveCloud cloud) {
			oneDriveImpl.logout();
		}
	}

}
