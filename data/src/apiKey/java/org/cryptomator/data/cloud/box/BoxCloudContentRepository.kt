package org.cryptomator.data.cloud.box

import android.content.Context
import org.cryptomator.data.cloud.InterceptingCloudContentRepository
import org.cryptomator.domain.BoxCloud
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.exception.CloudNodeAlreadyExistsException
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

internal class BoxCloudContentRepository(private val cloud: BoxCloud, api: BoxApi, context: Context) :
	InterceptingCloudContentRepository<BoxCloud, BoxNode, BoxFolder, BoxFile>(Intercepted(cloud, api, context)) {

	@Throws(BackendException::class)
	override fun throwWrappedIfRequired(e: Exception) {
		throwApiExceptionIfRequired(e)
		throwNetworkConnectionExceptionIfRequired(e)
	}

	@Throws(BackendException::class)
	private fun throwApiExceptionIfRequired(e: Exception) {
		val apiException = ExceptionUtil.extract(e, BoxApiException::class.java)
		if (apiException.isPresent) {
			when {
				apiException.get().isAuthenticationError -> throw WrongCredentialsException(cloud)
				apiException.get().isNotFound -> throw NoSuchCloudFileException()
				apiException.get().isConflict -> throw CloudNodeAlreadyExistsException(e.message)
			}
		}
	}

	@Throws(NetworkConnectionException::class)
	private fun throwNetworkConnectionExceptionIfRequired(e: Exception) {
		if (ExceptionUtil.contains(e, IOException::class.java)) {
			throw NetworkConnectionException(e)
		}
	}

	private class Intercepted(cloud: BoxCloud, api: BoxApi, context: Context) : CloudContentRepository<BoxCloud, BoxNode, BoxFolder, BoxFile> {

		private val impl: BoxImpl = BoxImpl(cloud, api, BoxIdCache(), context)

		override fun root(cloud: BoxCloud): BoxFolder {
			return impl.root()
		}

		override fun resolve(cloud: BoxCloud, path: String): BoxFolder {
			return impl.resolve(path)
		}

		override fun file(parent: BoxFolder, name: String): BoxFile {
			return impl.file(parent, name, null)
		}

		override fun file(parent: BoxFolder, name: String, size: Long?): BoxFile {
			return impl.file(parent, name, size)
		}

		override fun folder(parent: BoxFolder, name: String): BoxFolder {
			return impl.folder(parent, name)
		}

		@Throws(BackendException::class)
		override fun exists(node: BoxNode): Boolean {
			return try {
				impl.exists(node)
			} catch (e: IOException) {
				throw FatalBackendException(e)
			}
		}

		@Throws(BackendException::class)
		override fun list(folder: BoxFolder): List<BoxNode> {
			return try {
				impl.list(folder)
			} catch (e: IOException) {
				throw FatalBackendException(e)
			}
		}

		@Throws(BackendException::class)
		override fun create(folder: BoxFolder): BoxFolder {
			return try {
				impl.create(folder)
			} catch (e: IOException) {
				throw FatalBackendException(e)
			}
		}

		@Throws(BackendException::class)
		override fun move(source: BoxFolder, target: BoxFolder): BoxFolder {
			return try {
				impl.move(source, target) as BoxFolder
			} catch (e: IOException) {
				throw FatalBackendException(e)
			}
		}

		@Throws(BackendException::class)
		override fun move(source: BoxFile, target: BoxFile): BoxFile {
			return try {
				impl.move(source, target) as BoxFile
			} catch (e: IOException) {
				throw FatalBackendException(e)
			}
		}

		@Throws(BackendException::class)
		override fun write(file: BoxFile, data: DataSource, progressAware: ProgressAware<UploadState>, replace: Boolean, size: Long): BoxFile {
			return try {
				impl.write(file, data, progressAware, replace, size)
			} catch (e: IOException) {
				throw FatalBackendException(e)
			}
		}

		@Throws(BackendException::class)
		override fun read(file: BoxFile, encryptedTmpFile: File?, data: OutputStream, progressAware: ProgressAware<DownloadState>) {
			try {
				impl.read(file, encryptedTmpFile, data, progressAware)
			} catch (e: IOException) {
				throw FatalBackendException(e)
			}
		}

		@Throws(BackendException::class)
		override fun delete(node: BoxNode) {
			try {
				impl.delete(node)
			} catch (e: IOException) {
				throw FatalBackendException(e)
			}
		}

		@Throws(BackendException::class)
		override fun checkAuthenticationAndRetrieveCurrentAccount(cloud: BoxCloud): String {
			return try {
				impl.currentAccount()
			} catch (e: IOException) {
				throw FatalBackendException(e)
			}
		}

		override fun logout(cloud: BoxCloud) {
			impl.logout()
		}
	}
}
