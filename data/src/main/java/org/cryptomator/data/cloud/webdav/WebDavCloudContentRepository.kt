package org.cryptomator.data.cloud.webdav

import android.content.Context
import org.cryptomator.data.cloud.InterceptingCloudContentRepository
import org.cryptomator.data.cloud.webdav.network.ConnectionHandlerHandlerImpl
import org.cryptomator.domain.WebDavCloud
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.exception.CloudNodeAlreadyExistsException
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.domain.exception.ForbiddenException
import org.cryptomator.domain.exception.NetworkConnectionException
import org.cryptomator.domain.exception.NoSuchCloudFileException
import org.cryptomator.domain.exception.NotFoundException
import org.cryptomator.domain.exception.NotImplementedException
import org.cryptomator.domain.exception.NotTrustableCertificateException
import org.cryptomator.domain.exception.UnauthorizedException
import org.cryptomator.domain.exception.authentication.WebDavCertificateUntrustedAuthenticationException
import org.cryptomator.domain.exception.authentication.WebDavNotSupportedException
import org.cryptomator.domain.exception.authentication.WebDavServerNotFoundException
import org.cryptomator.domain.exception.authentication.WrongCredentialsException
import org.cryptomator.domain.repository.CloudContentRepository
import org.cryptomator.domain.usecases.ProgressAware
import org.cryptomator.domain.usecases.cloud.DataSource
import org.cryptomator.domain.usecases.cloud.DownloadState
import org.cryptomator.domain.usecases.cloud.UploadState
import org.cryptomator.util.ExceptionUtil
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Singleton
import javax.net.ssl.SSLHandshakeException

@Singleton
internal class WebDavCloudContentRepository(private val cloud: WebDavCloud, connectionHandlerHandler: ConnectionHandlerHandlerImpl, context: Context) :
	InterceptingCloudContentRepository<WebDavCloud, WebDavNode, WebDavFolder, WebDavFile>(Intercepted(cloud, connectionHandlerHandler, context)) {

	@Throws(BackendException::class)
	override fun throwWrappedIfRequired(e: Exception) {
		throwNetworkConnectionExceptionIfRequired(e)
		throwCertificateUntrustedExceptionIfRequired(e)
		throwForbiddenExceptionIfRequired(e)
		throwUnauthorizedExceptionIfRequired(e)
		throwNotImplementedExceptionIfRequired(e)
		throwServerNotFoundExceptionIfRequired(e)
	}

	private fun throwServerNotFoundExceptionIfRequired(e: Exception) {
		if (ExceptionUtil.contains(e, UnknownHostException::class.java)) {
			throw WebDavServerNotFoundException(cloud)
		}
	}

	private fun throwNotImplementedExceptionIfRequired(e: Exception) {
		if (ExceptionUtil.contains(e, NotImplementedException::class.java)) {
			throw WebDavNotSupportedException(cloud)
		}
	}

	private fun throwUnauthorizedExceptionIfRequired(e: Exception) {
		if (ExceptionUtil.contains(e, UnauthorizedException::class.java)) {
			throw WrongCredentialsException(cloud)
		}
	}

	private fun throwForbiddenExceptionIfRequired(e: Exception) {
		if (ExceptionUtil.contains(e, ForbiddenException::class.java)) {
			throw WrongCredentialsException(cloud)
		}
	}

	private fun throwCertificateUntrustedExceptionIfRequired(e: Exception) {
		val notTrustableCertificateException = ExceptionUtil.extract(e, NotTrustableCertificateException::class.java)
		if (notTrustableCertificateException.isPresent) {
			throw WebDavCertificateUntrustedAuthenticationException(cloud, notTrustableCertificateException.get().message)
		}
		val sslHandshakeException = ExceptionUtil.extract(e, SSLHandshakeException::class.java)
		if (sslHandshakeException.isPresent && containsCertificate(e.message)) {
			throw WebDavCertificateUntrustedAuthenticationException(cloud, sslHandshakeException.get().message)
		}
	}

	private fun containsCertificate(message: String?): Boolean {
		return message != null && message.contains(START_OF_CERTIFICATE)
	}

	@Throws(NetworkConnectionException::class)
	private fun throwNetworkConnectionExceptionIfRequired(e: Exception) {
		if (ExceptionUtil.contains(e, SocketTimeoutException::class.java)) {
			throw NetworkConnectionException(e)
		}
	}

	private class Intercepted constructor(cloud: WebDavCloud, connectionHandler: ConnectionHandlerHandlerImpl, context: Context) : CloudContentRepository<WebDavCloud, WebDavNode, WebDavFolder, WebDavFile> {

		private val webDavImpl: WebDavImpl = WebDavImpl(cloud, connectionHandler, context)

		override fun root(cloud: WebDavCloud): WebDavFolder {
			return webDavImpl.root()
		}

		override fun resolve(cloud: WebDavCloud, path: String): WebDavFolder {
			return webDavImpl.resolve(path)
		}

		@Throws(BackendException::class)
		override fun file(parent: WebDavFolder, name: String): WebDavFile {
			return webDavImpl.file(parent, name, null)
		}

		@Throws(BackendException::class)
		override fun file(parent: WebDavFolder, name: String, size: Long?): WebDavFile {
			return webDavImpl.file(parent, name, size)
		}

		override fun folder(parent: WebDavFolder, name: String): WebDavFolder {
			return webDavImpl.folder(parent, name)
		}

		@Throws(BackendException::class)
		override fun exists(node: WebDavNode): Boolean {
			return webDavImpl.exists(node)
		}

		@Throws(BackendException::class)
		override fun list(folder: WebDavFolder): List<WebDavNode> {
			return try {
				webDavImpl.list(folder)
			} catch (e: BackendException) {
				if (ExceptionUtil.contains(e, NotFoundException::class.java)) {
					throw NoSuchCloudFileException()
				}
				throw e
			}
		}

		@Throws(BackendException::class)
		override fun create(folder: WebDavFolder): WebDavFolder {
			return webDavImpl.create(folder)
		}

		@Throws(BackendException::class)
		override fun move(source: WebDavFolder, target: WebDavFolder): WebDavFolder {
			return try {
				webDavImpl.move(source, target)
			} catch (e: BackendException) {
				if (ExceptionUtil.contains(e, NotFoundException::class.java)) {
					throw NoSuchCloudFileException(source.name)
				} else if (ExceptionUtil.contains(e, CloudNodeAlreadyExistsException::class.java)) {
					throw CloudNodeAlreadyExistsException(target.name)
				}
				throw e
			}
		}

		@Throws(BackendException::class)
		override fun move(source: WebDavFile, target: WebDavFile): WebDavFile {
			return webDavImpl.move(source, target)
		}

		@Throws(BackendException::class)
		override fun write(file: WebDavFile, data: DataSource, progressAware: ProgressAware<UploadState>, replace: Boolean, size: Long): WebDavFile {
			return try {
				webDavImpl.write(file, data, progressAware, replace, size)
			} catch (e: BackendException) {
				when {
					ExceptionUtil.contains(e, NotFoundException::class.java) -> {
						throw NoSuchCloudFileException(file.name)
					}
					e is IOException -> {
						throw FatalBackendException(e)
					}
					e is FatalBackendException -> {
						throw e
					}
					else -> {
						throw FatalBackendException(e)
					}
				}
			} catch (e: IOException) {
				when {
					ExceptionUtil.contains(e, NotFoundException::class.java) -> {
						throw NoSuchCloudFileException(file.name)
					}
					e is FatalBackendException -> {
						throw e
					}
					else -> {
						throw FatalBackendException(e)
					}
				}
			}
		}

		@Throws(BackendException::class)
		override fun read(file: WebDavFile, encryptedTmpFile: File?, data: OutputStream, progressAware: ProgressAware<DownloadState>) {
			try {
				webDavImpl.read(file, data, progressAware)
			} catch (e: BackendException) {
				if (ExceptionUtil.contains(e, NotFoundException::class.java)) {
					throw NoSuchCloudFileException(file.name)
				} else if (e is IOException) {
					throw FatalBackendException(e)
				} else if (e is FatalBackendException) {
					throw e
				}
			} catch (e: IOException) {
				if (ExceptionUtil.contains(e, NotFoundException::class.java)) {
					throw NoSuchCloudFileException(file.name)
				} else if (e is FatalBackendException) {
					throw e
				}
			}
		}

		@Throws(BackendException::class)
		override fun delete(node: WebDavNode) {
			try {
				webDavImpl.delete(node)
			} catch (e: BackendException) {
				if (ExceptionUtil.contains(e, NotFoundException::class.java)) {
					throw NoSuchCloudFileException(node.name)
				}
				throw e
			}
		}

		@Throws(BackendException::class)
		override fun checkAuthenticationAndRetrieveCurrentAccount(cloud: WebDavCloud): String {
			return webDavImpl.currentAccount()
		}

		override fun logout(cloud: WebDavCloud) {
			// empty
		}

	}

	companion object {

		private val START_OF_CERTIFICATE: CharSequence = "-----BEGIN CERTIFICATE-----"
	}
}
