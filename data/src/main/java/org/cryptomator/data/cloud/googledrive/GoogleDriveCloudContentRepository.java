package org.cryptomator.data.cloud.googledrive;

import android.content.Context;

import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpStatusCodes;

import org.cryptomator.data.cloud.InterceptingCloudContentRepository;
import org.cryptomator.domain.CloudNode;
import org.cryptomator.domain.GoogleDriveCloud;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.FatalBackendException;
import org.cryptomator.domain.exception.NetworkConnectionException;
import org.cryptomator.domain.exception.NoSuchCloudFileException;
import org.cryptomator.domain.exception.authentication.UserRecoverableAuthenticationException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.domain.usecases.ProgressAware;
import org.cryptomator.domain.usecases.cloud.DataSource;
import org.cryptomator.domain.usecases.cloud.DownloadState;
import org.cryptomator.domain.usecases.cloud.UploadState;
import org.cryptomator.util.ExceptionUtil;
import org.cryptomator.util.Optional;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.util.List;

import static org.cryptomator.util.ExceptionUtil.contains;
import static org.cryptomator.util.ExceptionUtil.extract;

class GoogleDriveCloudContentRepository extends InterceptingCloudContentRepository<GoogleDriveCloud, GoogleDriveNode, GoogleDriveFolder, GoogleDriveFile> {

	private final GoogleDriveCloud cloud;

	GoogleDriveCloudContentRepository(Context context, GoogleDriveCloud cloud, GoogleDriveIdCache idCache) {
		super(new Intercepted(context, cloud, idCache));
		this.cloud = cloud;
	}

	@Override
	protected void throwWrappedIfRequired(Exception e) throws BackendException {
		throwConnectionErrorIfRequired(e);
		throwUserRecoverableAuthenticationExceptionIfRequired(e);
		throwNoSuchCloudFileExceptionIfRequired(e);
	}

	private void throwUserRecoverableAuthenticationExceptionIfRequired(Exception e) {
		Optional<UserRecoverableAuthIOException> userRecoverableAuthIOException = extract(e, UserRecoverableAuthIOException.class);
		if (userRecoverableAuthIOException.isPresent()) {
			throw new UserRecoverableAuthenticationException(cloud, userRecoverableAuthIOException.get().getIntent());
		}
	}

	private void throwConnectionErrorIfRequired(Exception e) throws NetworkConnectionException {
		if (contains(e, SocketTimeoutException.class) || contains(e, IOException.class, ExceptionUtil.thatHasMessage("NetworkError"))) {
			throw new NetworkConnectionException(e);
		}
	}

	private void throwNoSuchCloudFileExceptionIfRequired(Exception e) throws NoSuchCloudFileException {
		if (contains(e, GoogleJsonResponseException.class)) {
			if (extract(e, GoogleJsonResponseException.class).get().getStatusCode() == HttpStatusCodes.STATUS_CODE_NOT_FOUND) {
				throw new NoSuchCloudFileException();
			}
		}
	}

	private static class Intercepted implements CloudContentRepository<GoogleDriveCloud, GoogleDriveNode, GoogleDriveFolder, GoogleDriveFile> {

		private final GoogleDriveImpl impl;

		public Intercepted(Context context, GoogleDriveCloud cloud, GoogleDriveIdCache idCache) {
			this.impl = new GoogleDriveImpl(context, cloud, idCache);
		}

		@Override
		public GoogleDriveFolder root(GoogleDriveCloud cloud) throws BackendException {
			return impl.root();
		}

		@Override
		public GoogleDriveFolder resolve(GoogleDriveCloud cloud, String path) throws BackendException {
			try {
				return impl.resolve(path);
			} catch (IOException e) {
				throw new FatalBackendException(e);
			}
		}

		@Override
		public GoogleDriveFile file(GoogleDriveFolder parent, String name) throws BackendException {
			try {
				return impl.file(parent, name);
			} catch (IOException e) {
				throw new FatalBackendException(e);
			}
		}

		@Override
		public GoogleDriveFile file(GoogleDriveFolder parent, String name, Optional<Long> size) throws BackendException {
			try {
				return impl.file(parent, name, size);
			} catch (IOException e) {
				throw new FatalBackendException(e);
			}
		}

		@Override
		public GoogleDriveFolder folder(GoogleDriveFolder parent, String name) throws BackendException {
			try {
				return impl.folder(parent, name);
			} catch (IOException e) {
				throw new FatalBackendException(e);
			}
		}

		@Override
		public boolean exists(GoogleDriveNode node) throws BackendException {
			try {
				return impl.exists(node);
			} catch (IOException e) {
				throw new FatalBackendException(e);
			}
		}

		@Override
		public List<CloudNode> list(GoogleDriveFolder folder) throws BackendException {
			try {
				return impl.list(folder);
			} catch (IOException e) {
				throw new FatalBackendException(e);
			}
		}

		@Override
		public GoogleDriveFolder create(GoogleDriveFolder folder) throws BackendException {
			try {
				return impl.create(folder);
			} catch (IOException e) {
				throw new FatalBackendException(e);
			}
		}

		@Override
		public GoogleDriveFolder move(GoogleDriveFolder source, GoogleDriveFolder target) throws BackendException {
			try {
				if (source.getDriveId() == null) {
					throw new NoSuchCloudFileException(source.getName());
				}
				return (GoogleDriveFolder) impl.move(source, target);
			} catch (IOException e) {
				throw new FatalBackendException(e);
			}
		}

		@Override
		public GoogleDriveFile move(GoogleDriveFile source, GoogleDriveFile target) throws BackendException {
			try {
				return (GoogleDriveFile) impl.move(source, target);
			} catch (IOException e) {
				throw new FatalBackendException(e);
			}
		}

		@Override
		public GoogleDriveFile write(GoogleDriveFile file, DataSource data, ProgressAware<UploadState> progressAware, boolean replace, long size) throws BackendException {
			try {
				return impl.write(file, data, progressAware, replace, size);
			} catch (IOException e) {
				throw new FatalBackendException(e);
			}
		}

		@Override
		public void read(GoogleDriveFile file, Optional<File> encryptedTmpFile, OutputStream data, ProgressAware<DownloadState> progressAware) throws BackendException {
			try {
				if (file.getDriveId() == null) {
					throw new NoSuchCloudFileException(file.getName());
				}
				impl.read(file, encryptedTmpFile, data, progressAware);
			} catch (IOException e) {
				throw new FatalBackendException(e);
			}
		}

		@Override
		public void delete(GoogleDriveNode node) throws BackendException {
			try {
				impl.delete(node);
			} catch (IOException e) {
				throw new FatalBackendException(e);
			}
		}

		@Override
		public String checkAuthenticationAndRetrieveCurrentAccount(GoogleDriveCloud cloud) throws BackendException {
			try {
				return impl.currentAccount();
			} catch (IOException e) {
				throw new FatalBackendException(e);
			}
		}

		@Override
		public void logout(GoogleDriveCloud cloud) throws BackendException {
			// empty
		}
	}

}
