package org.cryptomator.data.cloud.webdav;

import org.cryptomator.data.cloud.InterceptingCloudContentRepository;
import org.cryptomator.data.cloud.webdav.network.ConnectionHandlerHandlerImpl;
import org.cryptomator.domain.CloudNode;
import org.cryptomator.domain.WebDavCloud;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.CloudNodeAlreadyExistsException;
import org.cryptomator.domain.exception.FatalBackendException;
import org.cryptomator.domain.exception.ForbiddenException;
import org.cryptomator.domain.exception.NetworkConnectionException;
import org.cryptomator.domain.exception.NoSuchCloudFileException;
import org.cryptomator.domain.exception.NotFoundException;
import org.cryptomator.domain.exception.NotImplementedException;
import org.cryptomator.domain.exception.NotTrustableCertificateException;
import org.cryptomator.domain.exception.UnauthorizedException;
import org.cryptomator.domain.exception.authentication.WebDavCertificateUntrustedAuthenticationException;
import org.cryptomator.domain.exception.authentication.WebDavNotSupportedException;
import org.cryptomator.domain.exception.authentication.WebDavServerNotFoundException;
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
import java.net.UnknownHostException;
import java.util.List;

import javax.inject.Singleton;
import javax.net.ssl.SSLHandshakeException;

import static org.cryptomator.util.ExceptionUtil.contains;
import static org.cryptomator.util.ExceptionUtil.extract;

@Singleton
class WebDavCloudContentRepository extends InterceptingCloudContentRepository<WebDavCloud, WebDavNode, WebDavFolder, WebDavFile> {

	private static final CharSequence START_OF_CERTIFICATE = "-----BEGIN CERTIFICATE-----";
	private final WebDavCloud cloud;

	WebDavCloudContentRepository(WebDavCloud cloud, ConnectionHandlerHandlerImpl connectionHandlerHandler) {
		super(new Intercepted(cloud, connectionHandlerHandler));
		this.cloud = cloud;
	}

	@Override
	protected void throwWrappedIfRequired(Exception e) throws BackendException {
		throwNetworkConnectionExceptionIfRequired(e);
		throwCertificateUntrustedExceptionIfRequired(e);
		throwForbiddenExceptionIfRequired(e);
		throwUnauthorizedExceptionIfRequired(e);
		throwNotImplementedExceptionIfRequired(e);
		throwServerNotFoundExceptionIfRequired(e);
	}

	private void throwServerNotFoundExceptionIfRequired(Exception e) {
		if (contains(e, UnknownHostException.class)) {
			throw new WebDavServerNotFoundException(cloud);
		}
	}

	private void throwNotImplementedExceptionIfRequired(Exception e) {
		if (contains(e, NotImplementedException.class)) {
			throw new WebDavNotSupportedException(cloud);
		}
	}

	private void throwUnauthorizedExceptionIfRequired(Exception e) {
		if (contains(e, UnauthorizedException.class)) {
			throw new WrongCredentialsException(cloud);
		}
	}

	private void throwForbiddenExceptionIfRequired(Exception e) {
		if (contains(e, ForbiddenException.class)) {
			throw new WrongCredentialsException(cloud);
		}
	}

	private void throwCertificateUntrustedExceptionIfRequired(Exception e) {
		Optional<NotTrustableCertificateException> notTrustableCertificateException = extract(e, NotTrustableCertificateException.class);
		if (notTrustableCertificateException.isPresent()) {
			throw new WebDavCertificateUntrustedAuthenticationException(cloud, notTrustableCertificateException.get().getMessage());
		}
		Optional<SSLHandshakeException> sslHandshakeException = extract(e, SSLHandshakeException.class);
		if (sslHandshakeException.isPresent() && containsCertificate(e.getMessage())) {
			throw new WebDavCertificateUntrustedAuthenticationException(cloud, sslHandshakeException.get().getMessage());
		}
	}

	private boolean containsCertificate(String message) {
		return message != null && message.contains(START_OF_CERTIFICATE);
	}

	private void throwNetworkConnectionExceptionIfRequired(Exception e) throws NetworkConnectionException {
		if (contains(e, SocketTimeoutException.class)) {
			throw new NetworkConnectionException(e);
		}
	}

	private static class Intercepted implements CloudContentRepository<WebDavCloud, WebDavNode, WebDavFolder, WebDavFile> {

		private final WebDavImpl webDavImpl;

		Intercepted(WebDavCloud cloud, ConnectionHandlerHandlerImpl connectionHandler) {
			this.webDavImpl = new WebDavImpl(cloud, connectionHandler);
		}

		public WebDavFolder root(WebDavCloud cloud) {
			return webDavImpl.root();
		}

		@Override
		public WebDavFolder resolve(WebDavCloud cloud, String path) throws BackendException {
			return webDavImpl.resolve(path);
		}

		@Override
		public WebDavFile file(WebDavFolder parent, String name) throws BackendException {
			return webDavImpl.file(parent, name);
		}

		@Override
		public WebDavFile file(WebDavFolder parent, String name, Optional<Long> size) throws BackendException {
			return webDavImpl.file(parent, name, size);
		}

		@Override
		public WebDavFolder folder(WebDavFolder parent, String name) {
			return webDavImpl.folder(parent, name);
		}

		@Override
		public boolean exists(WebDavNode node) throws BackendException {
			return webDavImpl.exists(node);
		}

		@Override
		public List<CloudNode> list(WebDavFolder folder) throws BackendException {
			try {
				return webDavImpl.list(folder);
			} catch (BackendException e) {
				if (contains(e, NotFoundException.class)) {
					throw new NoSuchCloudFileException();
				}
				throw e;
			}
		}

		@Override
		public WebDavFolder create(WebDavFolder folder) throws BackendException {
			return webDavImpl.create(folder);
		}

		@Override
		public WebDavFolder move(WebDavFolder source, WebDavFolder target) throws BackendException {
			try {
				return webDavImpl.move(source, target);
			} catch (BackendException e) {
				if (contains(e, NotFoundException.class)) {
					throw new NoSuchCloudFileException(source.getName());
				} else if (contains(e, CloudNodeAlreadyExistsException.class)) {
					throw new CloudNodeAlreadyExistsException(target.getName());
				}
				throw e;
			}
		}

		@Override
		public WebDavFile move(WebDavFile source, WebDavFile target) throws BackendException {
			return webDavImpl.move(source, target);
		}

		@Override
		public WebDavFile write(WebDavFile uploadFile, DataSource data, ProgressAware<UploadState> progressAware, boolean replace, long size) throws BackendException {
			try {
				return webDavImpl.write(uploadFile, data, progressAware, replace, size);
			} catch (BackendException | IOException e) {
				if (contains(e, NotFoundException.class)) {
					throw new NoSuchCloudFileException(uploadFile.getName());
				} else if (e instanceof IOException) {
					throw new FatalBackendException(e);
				} else if (e instanceof FatalBackendException) {
					throw (FatalBackendException) e;
				} else {
					throw new FatalBackendException(e);
				}
			}
		}

		@Override
		public void read(WebDavFile file, Optional<File> tmpEncryptedFile, OutputStream data, ProgressAware<DownloadState> progressAware) throws BackendException {
			try {
				webDavImpl.read(file, data, progressAware);
			} catch (BackendException | IOException e) {
				if (contains(e, NotFoundException.class)) {
					throw new NoSuchCloudFileException(file.getName());
				} else if (e instanceof IOException) {
					throw new FatalBackendException(e);
				} else if (e instanceof FatalBackendException) {
					throw (FatalBackendException) e;
				}
			}
		}

		@Override
		public void delete(WebDavNode node) throws BackendException {
			try {
				webDavImpl.delete(node);
			} catch (BackendException e) {
				if (contains(e, NotFoundException.class)) {
					throw new NoSuchCloudFileException(node.getName());
				}
				throw e;
			}
		}

		@Override
		public String checkAuthenticationAndRetrieveCurrentAccount(WebDavCloud cloud) throws BackendException {
			return webDavImpl.currentAccount();
		}

		@Override
		public void logout(WebDavCloud cloud) {
			// empty
		}
	}

}
