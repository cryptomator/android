package org.cryptomator.data.cloud.googledrive

import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.HttpStatusCodes
import org.cryptomator.data.cloud.InterceptingCloudContentRepository
import org.cryptomator.domain.GoogleDriveCloud
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.domain.exception.NetworkConnectionException
import org.cryptomator.domain.exception.NoSuchCloudFileException
import org.cryptomator.domain.exception.authentication.UserRecoverableAuthenticationException
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

internal class GoogleDriveCloudContentRepository(context: Context, private val cloud: GoogleDriveCloud, idCache: GoogleDriveIdCache) :
	InterceptingCloudContentRepository<GoogleDriveCloud, GoogleDriveNode, GoogleDriveFolder, GoogleDriveFile>(Intercepted(context, cloud, idCache)) {

	@Throws(BackendException::class)
	override fun throwWrappedIfRequired(e: Exception) {
		throwConnectionErrorIfRequired(e)
		throwUserRecoverableAuthenticationExceptionIfRequired(e)
		throwNoSuchCloudFileExceptionIfRequired(e)
	}

	private fun throwUserRecoverableAuthenticationExceptionIfRequired(e: Exception) {
		val userRecoverableAuthIOException = ExceptionUtil.extract(e, UserRecoverableAuthIOException::class.java)
		if (userRecoverableAuthIOException.isPresent) {
			throw UserRecoverableAuthenticationException(cloud, userRecoverableAuthIOException.get().intent)
		}
	}

	@Throws(NetworkConnectionException::class)
	private fun throwConnectionErrorIfRequired(e: Exception) {
		if (ExceptionUtil.contains(e, SocketTimeoutException::class.java) || ExceptionUtil.contains(e, IOException::class.java, ExceptionUtil.thatHasMessage("NetworkError"))) {
			throw NetworkConnectionException(e)
		}
	}

	@Throws(NoSuchCloudFileException::class)
	private fun throwNoSuchCloudFileExceptionIfRequired(e: Exception) {
		if (ExceptionUtil.contains(e, GoogleJsonResponseException::class.java)) {
			if (ExceptionUtil.extract(e, GoogleJsonResponseException::class.java).get().statusCode == HttpStatusCodes.STATUS_CODE_NOT_FOUND) {
				throw NoSuchCloudFileException()
			}
		}
	}

	private class Intercepted(context: Context, cloud: GoogleDriveCloud, idCache: GoogleDriveIdCache) : CloudContentRepository<GoogleDriveCloud, GoogleDriveNode, GoogleDriveFolder, GoogleDriveFile> {

		private val impl: GoogleDriveImpl = GoogleDriveImpl(context, cloud, idCache)

		@Throws(BackendException::class)
		override fun root(cloud: GoogleDriveCloud): GoogleDriveFolder {
			return impl.root()
		}

		override fun resolve(cloud: GoogleDriveCloud, path: String): GoogleDriveFolder {
			return try {
				impl.resolve(path)
			} catch (e: IOException) {
				throw FatalBackendException(e)
			}
		}

		@Throws(BackendException::class)
		override fun file(parent: GoogleDriveFolder, name: String): GoogleDriveFile {
			return try {
				impl.file(parent, name, null)
			} catch (e: IOException) {
				throw FatalBackendException(e)
			}
		}

		@Throws(BackendException::class)
		override fun file(parent: GoogleDriveFolder, name: String, size: Long?): GoogleDriveFile {
			return try {
				impl.file(parent, name, size)
			} catch (e: IOException) {
				throw FatalBackendException(e)
			}
		}

		@Throws(BackendException::class)
		override fun folder(parent: GoogleDriveFolder, name: String): GoogleDriveFolder {
			return try {
				impl.folder(parent, name)
			} catch (e: IOException) {
				throw FatalBackendException(e)
			}
		}

		@Throws(BackendException::class)
		override fun exists(node: GoogleDriveNode): Boolean {
			return try {
				impl.exists(node)
			} catch (e: IOException) {
				throw FatalBackendException(e)
			}
		}

		@Throws(BackendException::class)
		override fun list(folder: GoogleDriveFolder): List<GoogleDriveNode> {
			return try {
				impl.list(folder)
			} catch (e: IOException) {
				throw FatalBackendException(e)
			}
		}

		@Throws(BackendException::class)
		override fun create(folder: GoogleDriveFolder): GoogleDriveFolder {
			return try {
				impl.create(folder)
			} catch (e: IOException) {
				throw FatalBackendException(e)
			}
		}

		@Throws(BackendException::class)
		override fun move(source: GoogleDriveFolder, target: GoogleDriveFolder): GoogleDriveFolder {
			return try {
				if (source.driveId == null) {
					throw NoSuchCloudFileException(source.name)
				}
				impl.move(source, target) as GoogleDriveFolder
			} catch (e: IOException) {
				throw FatalBackendException(e)
			}
		}

		@Throws(BackendException::class)
		override fun move(source: GoogleDriveFile, target: GoogleDriveFile): GoogleDriveFile {
			return try {
				impl.move(source, target) as GoogleDriveFile
			} catch (e: IOException) {
				throw FatalBackendException(e)
			}
		}

		@Throws(BackendException::class)
		override fun write(file: GoogleDriveFile, data: DataSource, progressAware: ProgressAware<UploadState>, replace: Boolean, size: Long): GoogleDriveFile {
			return try {
				impl.write(file, data, progressAware, replace, size)
			} catch (e: IOException) {
				throw FatalBackendException(e)
			}
		}

		@Throws(BackendException::class)
		override fun read(file: GoogleDriveFile, encryptedTmpFile: File?, data: OutputStream, progressAware: ProgressAware<DownloadState>) {
			try {
				if (file.driveId == null) {
					throw NoSuchCloudFileException(file.name)
				}
				impl.read(file, encryptedTmpFile, data, progressAware)
			} catch (e: IOException) {
				throw FatalBackendException(e)
			}
		}

		@Throws(BackendException::class)
		override fun delete(node: GoogleDriveNode) {
			try {
				impl.delete(node)
			} catch (e: IOException) {
				throw FatalBackendException(e)
			}
		}

		@Throws(BackendException::class)
		override fun checkAuthenticationAndRetrieveCurrentAccount(cloud: GoogleDriveCloud): String {
			return try {
				impl.currentAccount()
			} catch (e: IOException) {
				throw FatalBackendException(e)
			}
		}

		@Throws(BackendException::class)
		override fun logout(cloud: GoogleDriveCloud) {
			impl.logout()
		}

	}
}
