package org.cryptomator.data.cloud.onedrive

import android.content.Context
import com.microsoft.graph.core.GraphErrorCodes
import com.microsoft.graph.http.GraphServiceException
import com.microsoft.graph.requests.GraphServiceClient
import com.microsoft.identity.common.java.exception.ClientException
import org.cryptomator.data.cloud.InterceptingCloudContentRepository
import org.cryptomator.domain.OnedriveCloud
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.domain.exception.NetworkConnectionException
import org.cryptomator.domain.exception.NoSuchCloudFileException
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
import okhttp3.Request

internal class OnedriveCloudContentRepository(private val cloud: OnedriveCloud, context: Context, graphServiceClient: GraphServiceClient<Request>)
	: InterceptingCloudContentRepository<OnedriveCloud, OnedriveNode, OnedriveFolder, OnedriveFile>(Intercepted(cloud, context, graphServiceClient)) {

	@Throws(BackendException::class)
	override fun throwWrappedIfRequired(e: Exception) {
		throwNetworkConnectionExceptionIfRequired(e)
		throwWrongCredentialsExceptionIfRequired(e)
	}

	@Throws(NetworkConnectionException::class)
	private fun throwNetworkConnectionExceptionIfRequired(e: Exception) {
		if (ExceptionUtil.contains(e, SocketTimeoutException::class.java)) {
			throw NetworkConnectionException(e)
		}
	}

	private fun throwWrongCredentialsExceptionIfRequired(e: Exception) {
		if (isAuthenticationError(e)) {
			throw WrongCredentialsException(cloud)
		}
	}

	private fun isAuthenticationError(e: Throwable?): Boolean {
		return (e != null //
				&& (e is ClientException && e.errorCode == GraphErrorCodes.AUTHENTICATION_FAILURE.name //
				|| e is GraphServiceException && e.serviceError?.code?.equals("InvalidAuthenticationToken") == true
				|| isAuthenticationError(e.cause)))
	}

	private class Intercepted(cloud: OnedriveCloud, context: Context, graphServiceClient: GraphServiceClient<Request>) : CloudContentRepository<OnedriveCloud, OnedriveNode, OnedriveFolder, OnedriveFile> {

		private val oneDriveImpl: OnedriveImpl = OnedriveImpl(cloud, context, graphServiceClient, OnedriveIdCache())

		override fun root(cloud: OnedriveCloud): OnedriveFolder {
			return oneDriveImpl.root()
		}

		override fun resolve(cloud: OnedriveCloud, path: String): OnedriveFolder {
			return oneDriveImpl.resolve(path)
		}

		override fun file(parent: OnedriveFolder, name: String): OnedriveFile {
			return oneDriveImpl.file(parent, name)
		}

		override fun file(parent: OnedriveFolder, name: String, size: Long?): OnedriveFile {
			return oneDriveImpl.file(parent, name, size)
		}

		override fun folder(parent: OnedriveFolder, name: String): OnedriveFolder {
			return oneDriveImpl.folder(parent, name)
		}

		@Throws(BackendException::class)
		override fun exists(node: OnedriveNode): Boolean {
			return oneDriveImpl.exists(node)
		}

		@Throws(BackendException::class)
		override fun list(folder: OnedriveFolder): List<OnedriveNode> {
			return oneDriveImpl.list(folder)
		}

		@Throws(BackendException::class)
		override fun create(folder: OnedriveFolder): OnedriveFolder {
			return oneDriveImpl.create(folder)
		}

		@Throws(BackendException::class)
		override fun move(source: OnedriveFolder, target: OnedriveFolder): OnedriveFolder {
			return oneDriveImpl.move(source, target) as OnedriveFolder
		}

		@Throws(BackendException::class)
		override fun move(source: OnedriveFile, target: OnedriveFile): OnedriveFile {
			return oneDriveImpl.move(source, target) as OnedriveFile
		}

		@Throws(BackendException::class)
		override fun write(file: OnedriveFile, data: DataSource, progressAware: ProgressAware<UploadState>, replace: Boolean, size: Long): OnedriveFile {
			return try {
				oneDriveImpl.write(file, data, progressAware, replace, size)
			} catch (e: BackendException) {
				if (ExceptionUtil.contains(e, NoSuchCloudFileException::class.java)) {
					throw NoSuchCloudFileException(file.name)
				}
				throw e
			}
		}

		@Throws(BackendException::class)
		override fun read(file: OnedriveFile, encryptedTmpFile: File?, data: OutputStream, progressAware: ProgressAware<DownloadState>) {
			try {
				oneDriveImpl.read(file, encryptedTmpFile, data, progressAware)
			} catch (e: IOException) {
				when {
					ExceptionUtil.contains(e, NoSuchCloudFileException::class.java) -> {
						throw NoSuchCloudFileException(file.name)
					}
					else -> {
						throw FatalBackendException(e)
					}
				}
			} catch (e: BackendException) {
				when {
					ExceptionUtil.contains(e, NoSuchCloudFileException::class.java) -> {
						throw NoSuchCloudFileException(file.name)
					}
					else -> {
						throw e
					}
				}
			}
		}

		@Throws(BackendException::class)
		override fun delete(node: OnedriveNode) {
			oneDriveImpl.delete(node)
		}

		@Throws(BackendException::class)
		override fun checkAuthenticationAndRetrieveCurrentAccount(cloud: OnedriveCloud): String {
			return oneDriveImpl.currentAccount(cloud.username())
		}

		override fun logout(cloud: OnedriveCloud) {
			oneDriveImpl.logout()
		}

	}
}
