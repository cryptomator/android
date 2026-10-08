package org.cryptomator.data.cloud.microsoftgraph

import android.content.Context
import com.microsoft.graph.core.GraphErrorCodes
import com.microsoft.graph.http.GraphServiceException
import com.microsoft.graph.requests.GraphServiceClient
import com.microsoft.identity.common.java.exception.ClientException
import org.cryptomator.data.cloud.InterceptingCloudContentRepository
import org.cryptomator.domain.MicrosoftGraphCloud
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

internal class MicrosoftGraphCloudContentRepository(private val cloud: MicrosoftGraphCloud, graphServiceClient: GraphServiceClient<Request>, context: Context) :
	InterceptingCloudContentRepository<MicrosoftGraphCloud, MicrosoftGraphNode, MicrosoftGraphFolder, MicrosoftGraphFile>(Intercepted(cloud, graphServiceClient, context)) {

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

	private class Intercepted(cloud: MicrosoftGraphCloud, graphServiceClient: GraphServiceClient<Request>, context: Context) : CloudContentRepository<MicrosoftGraphCloud, MicrosoftGraphNode, MicrosoftGraphFolder, MicrosoftGraphFile> {

		private val microsoftGraphImpl: MicrosoftGraphImpl = MicrosoftGraphImpl(cloud, graphServiceClient, MicrosoftGraphIdCache(), context)

		override fun root(cloud: MicrosoftGraphCloud): MicrosoftGraphFolder {
			return microsoftGraphImpl.root()
		}

		override fun resolve(cloud: MicrosoftGraphCloud, path: String): MicrosoftGraphFolder {
			return microsoftGraphImpl.resolve(path)
		}

		override fun file(parent: MicrosoftGraphFolder, name: String): MicrosoftGraphFile {
			return microsoftGraphImpl.file(parent, name)
		}

		override fun file(parent: MicrosoftGraphFolder, name: String, size: Long?): MicrosoftGraphFile {
			return microsoftGraphImpl.file(parent, name, size)
		}

		override fun folder(parent: MicrosoftGraphFolder, name: String): MicrosoftGraphFolder {
			return microsoftGraphImpl.folder(parent, name)
		}

		@Throws(BackendException::class)
		override fun exists(node: MicrosoftGraphNode): Boolean {
			return microsoftGraphImpl.exists(node)
		}

		@Throws(BackendException::class)
		override fun list(folder: MicrosoftGraphFolder): List<MicrosoftGraphNode> {
			return microsoftGraphImpl.list(folder)
		}

		@Throws(BackendException::class)
		override fun create(folder: MicrosoftGraphFolder): MicrosoftGraphFolder {
			return microsoftGraphImpl.create(folder)
		}

		@Throws(BackendException::class)
		override fun move(source: MicrosoftGraphFolder, target: MicrosoftGraphFolder): MicrosoftGraphFolder {
			return microsoftGraphImpl.move(source, target) as MicrosoftGraphFolder
		}

		@Throws(BackendException::class)
		override fun move(source: MicrosoftGraphFile, target: MicrosoftGraphFile): MicrosoftGraphFile {
			return microsoftGraphImpl.move(source, target) as MicrosoftGraphFile
		}

		@Throws(BackendException::class)
		override fun write(file: MicrosoftGraphFile, data: DataSource, progressAware: ProgressAware<UploadState>, replace: Boolean, size: Long): MicrosoftGraphFile {
			return try {
				microsoftGraphImpl.write(file, data, progressAware, replace, size)
			} catch (e: BackendException) {
				if (ExceptionUtil.contains(e, NoSuchCloudFileException::class.java)) {
					throw NoSuchCloudFileException(file.name)
				}
				throw e
			}
		}

		@Throws(BackendException::class)
		override fun read(file: MicrosoftGraphFile, encryptedTmpFile: File?, data: OutputStream, progressAware: ProgressAware<DownloadState>) {
			try {
				microsoftGraphImpl.read(file, encryptedTmpFile, data, progressAware)
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
		override fun delete(node: MicrosoftGraphNode) {
			microsoftGraphImpl.delete(node)
		}

		@Throws(BackendException::class)
		override fun checkAuthenticationAndRetrieveCurrentAccount(cloud: MicrosoftGraphCloud): String {
			return microsoftGraphImpl.currentAccount(cloud.username())
		}

		override fun logout(cloud: MicrosoftGraphCloud) {
			microsoftGraphImpl.logout()
		}

	}
}
