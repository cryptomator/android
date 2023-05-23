package org.cryptomator.data.cloud.s3

import android.content.Context
import org.cryptomator.data.cloud.InterceptingCloudContentRepository
import org.cryptomator.data.cloud.s3.S3CloudApiExceptions.isAccessProblem
import org.cryptomator.data.cloud.s3.S3CloudApiExceptions.isNoSuchBucketException
import org.cryptomator.domain.S3Cloud
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.domain.exception.ForbiddenException
import org.cryptomator.domain.exception.NetworkConnectionException
import org.cryptomator.domain.exception.NoSuchBucketException
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
import io.minio.MinioClient
import io.minio.errors.ErrorResponseException

internal class S3CloudContentRepository(private val cloud: S3Cloud, client: MinioClient, context: Context) : InterceptingCloudContentRepository<S3Cloud, S3Node, S3Folder, S3File>(Intercepted(cloud, client, context)) {

	@Throws(BackendException::class)
	override fun throwWrappedIfRequired(e: Exception) {
		throwNoSuchBucketExceptionIfRequired(e)
		throwConnectionErrorIfRequired(e)
		throwWrongCredentialsExceptionIfRequired(e)
	}

	@Throws(NoSuchBucketException::class)
	private fun throwNoSuchBucketExceptionIfRequired(e: Exception) {
		if (e is ErrorResponseException) {
			val errorCode = e.errorResponse().code()
			if (isNoSuchBucketException(errorCode)) {
				throw NoSuchBucketException(cloud.s3Bucket())
			}
		}
	}

	@Throws(NetworkConnectionException::class)
	private fun throwConnectionErrorIfRequired(e: Exception) {
		if (ExceptionUtil.contains(e, IOException::class.java)) {
			throw NetworkConnectionException(e)
		}
	}

	private fun throwWrongCredentialsExceptionIfRequired(e: Exception) {
		if (e is ErrorResponseException) {
			val errorCode = e.errorResponse().code()
			if (isAccessProblem(errorCode)) {
				throw WrongCredentialsException(cloud)
			}
		} else if (e is ForbiddenException) {
			throw WrongCredentialsException(cloud)
		}
	}

	private class Intercepted(cloud: S3Cloud, client: MinioClient, context: Context) : CloudContentRepository<S3Cloud, S3Node, S3Folder, S3File> {

		private val cloud: S3Impl = S3Impl(cloud, client, context)

		override fun root(cloud: S3Cloud): S3Folder {
			return this.cloud.root()
		}

		override fun resolve(cloud: S3Cloud, path: String): S3Folder {
			return this.cloud.resolve(path)
		}

		@Throws(BackendException::class)
		override fun file(parent: S3Folder, name: String): S3File {
			return try {
				cloud.file(parent, name, null)
			} catch (ex: IOException) {
				throw FatalBackendException(ex)
			}
		}

		@Throws(BackendException::class)
		override fun file(parent: S3Folder, name: String, size: Long?): S3File {
			return try {
				cloud.file(parent, name, size)
			} catch (ex: IOException) {
				throw FatalBackendException(ex)
			}
		}

		@Throws(BackendException::class)
		override fun folder(parent: S3Folder, name: String): S3Folder {
			return cloud.folder(parent, name)
		}

		@Throws(BackendException::class)
		override fun exists(node: S3Node): Boolean {
			return cloud.exists(node)
		}

		@Throws(BackendException::class)
		override fun list(folder: S3Folder): List<S3Node> {
			return try {
				cloud.list(folder)
			} catch (e: IOException) {
				throw FatalBackendException(e)
			}
		}

		@Throws(BackendException::class)
		override fun create(folder: S3Folder): S3Folder {
			return try {
				cloud.create(folder)
			} catch (e: IOException) {
				throw FatalBackendException(e)
			}
		}

		@Throws(BackendException::class)
		override fun move(source: S3Folder, target: S3Folder): S3Folder {
			return try {
				cloud.move(source, target) as S3Folder
			} catch (e: IOException) {
				throw FatalBackendException(e)
			}
		}

		@Throws(BackendException::class)
		override fun move(source: S3File, target: S3File): S3File {
			return try {
				cloud.move(source, target) as S3File
			} catch (e: IOException) {
				throw FatalBackendException(e)
			}
		}

		@Throws(BackendException::class)
		override fun write(file: S3File, data: DataSource, progressAware: ProgressAware<UploadState>, replace: Boolean, size: Long): S3File {
			return try {
				cloud.write(file, data, progressAware, replace, size)
			} catch (e: IOException) {
				throw FatalBackendException(e)
			}
		}

		@Throws(BackendException::class)
		override fun read(file: S3File, encryptedTmpFile: File?, data: OutputStream, progressAware: ProgressAware<DownloadState>) {
			try {
				cloud.read(file, data, progressAware)
			} catch (e: IOException) {
				throw FatalBackendException(e)
			}
		}

		@Throws(BackendException::class)
		override fun delete(node: S3Node) {
			try {
				cloud.delete(node)
			} catch (e: IOException) {
				throw FatalBackendException(e)
			}
		}

		@Throws(BackendException::class)
		override fun checkAuthenticationAndRetrieveCurrentAccount(cloud: S3Cloud): String {
			return this.cloud.checkAuthentication()
		}

		@Throws(BackendException::class)
		override fun logout(cloud: S3Cloud) {
			this.cloud.logout()
		}

	}
}
