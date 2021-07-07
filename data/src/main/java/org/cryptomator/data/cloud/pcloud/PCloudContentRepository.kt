package org.cryptomator.data.cloud.pcloud

import android.content.Context
import com.pcloud.sdk.ApiError
import org.cryptomator.data.cloud.InterceptingCloudContentRepository
import org.cryptomator.domain.PCloud
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.domain.exception.NetworkConnectionException
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

internal class PCloudContentRepository(private val cloud: PCloud, context: Context) : InterceptingCloudContentRepository<PCloud, PCloudNode, PCloudFolder, PCloudFile>(Intercepted(cloud, context)) {

	@Throws(BackendException::class)
	override fun throwWrappedIfRequired(e: Exception) {
		throwConnectionErrorIfRequired(e)
		throwWrongCredentialsExceptionIfRequired(e)
	}

	@Throws(NetworkConnectionException::class)
	private fun throwConnectionErrorIfRequired(e: Exception) {
		if (ExceptionUtil.contains(e, IOException::class.java)) {
			throw NetworkConnectionException(e)
		}
	}

	private fun throwWrongCredentialsExceptionIfRequired(e: Exception) {
		if (e is ApiError) {
			val errorCode = e.errorCode()
			if (errorCode == PCloudApiError.PCloudApiErrorCodes.INVALID_ACCESS_TOKEN.value //
				|| errorCode == PCloudApiError.PCloudApiErrorCodes.ACCESS_TOKEN_REVOKED.value
			) {
				throw WrongCredentialsException(cloud)
			}
		}
	}

	private class Intercepted(cloud: PCloud, context: Context) : CloudContentRepository<PCloud, PCloudNode, PCloudFolder, PCloudFile> {

		private val cloud: PCloudImpl = PCloudImpl(context, cloud)

		override fun root(cloud: PCloud): PCloudFolder {
			return this.cloud.root()
		}

		@Throws(BackendException::class)
		override fun resolve(cloud: PCloud, path: String): PCloudFolder {
			return try {
				this.cloud.resolve(path)
			} catch (ex: IOException) {
				throw FatalBackendException(ex)
			}
		}

		@Throws(BackendException::class)
		override fun file(parent: PCloudFolder, name: String): PCloudFile {
			return try {
				cloud.file(parent, name, null)
			} catch (ex: IOException) {
				throw FatalBackendException(ex)
			}
		}

		@Throws(BackendException::class)
		override fun file(parent: PCloudFolder, name: String, size: Long?): PCloudFile {
			return try {
				cloud.file(parent, name, size)
			} catch (ex: IOException) {
				throw FatalBackendException(ex)
			}
		}

		@Throws(BackendException::class)
		override fun folder(parent: PCloudFolder, name: String): PCloudFolder {
			return try {
				cloud.folder(parent, name)
			} catch (ex: IOException) {
				throw FatalBackendException(ex)
			}
		}

		@Throws(BackendException::class)
		override fun exists(node: PCloudNode): Boolean {
			return try {
				cloud.exists(node)
			} catch (e: IOException) {
				throw FatalBackendException(e)
			}
		}

		@Throws(BackendException::class)
		override fun list(folder: PCloudFolder): List<PCloudNode> {
			return try {
				cloud.list(folder)
			} catch (e: IOException) {
				throw FatalBackendException(e)
			}
		}

		@Throws(BackendException::class)
		override fun create(folder: PCloudFolder): PCloudFolder {
			return try {
				cloud.create(folder)
			} catch (e: IOException) {
				throw FatalBackendException(e)
			}
		}

		@Throws(BackendException::class)
		override fun move(source: PCloudFolder, target: PCloudFolder): PCloudFolder {
			return try {
				cloud.move(source, target) as PCloudFolder
			} catch (e: IOException) {
				throw FatalBackendException(e)
			}
		}

		@Throws(BackendException::class)
		override fun move(source: PCloudFile, target: PCloudFile): PCloudFile {
			return try {
				cloud.move(source, target) as PCloudFile
			} catch (e: IOException) {
				throw FatalBackendException(e)
			}
		}

		@Throws(BackendException::class)
		override fun write(file: PCloudFile, data: DataSource, progressAware: ProgressAware<UploadState>, replace: Boolean, size: Long): PCloudFile {
			return try {
				cloud.write(file, data, progressAware, replace, size)
			} catch (e: IOException) {
				throw FatalBackendException(e)
			}
		}

		@Throws(BackendException::class)
		override fun read(file: PCloudFile, encryptedTmpFile: File?, data: OutputStream, progressAware: ProgressAware<DownloadState>) {
			try {
				cloud.read(file, encryptedTmpFile, data, progressAware)
			} catch (e: IOException) {
				throw FatalBackendException(e)
			}
		}

		@Throws(BackendException::class)
		override fun delete(node: PCloudNode) {
			try {
				cloud.delete(node)
			} catch (e: IOException) {
				throw FatalBackendException(e)
			}
		}

		@Throws(BackendException::class)
		override fun checkAuthenticationAndRetrieveCurrentAccount(cloud: PCloud): String {
			return try {
				this.cloud.currentAccount()
			} catch (e: IOException) {
				throw FatalBackendException(e)
			}
		}

		@Throws(BackendException::class)
		override fun logout(cloud: PCloud) {
			// empty
		}

	}
}
