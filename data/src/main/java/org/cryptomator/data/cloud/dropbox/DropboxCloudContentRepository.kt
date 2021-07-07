package org.cryptomator.data.cloud.dropbox

import android.content.Context
import com.dropbox.core.DbxException
import com.dropbox.core.InvalidAccessTokenException
import com.dropbox.core.NetworkIOException
import com.dropbox.core.v2.files.CreateFolderErrorException
import com.dropbox.core.v2.files.DeleteErrorException
import com.dropbox.core.v2.files.DownloadErrorException
import com.dropbox.core.v2.files.GetMetadataErrorException
import com.dropbox.core.v2.files.ListFolderErrorException
import com.dropbox.core.v2.files.RelocationErrorException
import org.cryptomator.data.cloud.InterceptingCloudContentRepository
import org.cryptomator.domain.DropboxCloud
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

internal class DropboxCloudContentRepository(private val cloud: DropboxCloud, context: Context) : InterceptingCloudContentRepository<DropboxCloud, DropboxNode, DropboxFolder, DropboxFile>(Intercepted(cloud, context)){

	@Throws(BackendException::class)
	override fun throwWrappedIfRequired(e: Exception) {
		throwConnectionErrorIfRequired(e)
		throwWrongCredentialsExceptionIfRequired(e)
	}

	@Throws(NetworkConnectionException::class)
	private fun throwConnectionErrorIfRequired(e: Exception) {
		if (ExceptionUtil.contains(e, NetworkIOException::class.java)) {
			throw NetworkConnectionException(e)
		}
	}

	private fun throwWrongCredentialsExceptionIfRequired(e: Exception) {
		if (ExceptionUtil.contains(e, InvalidAccessTokenException::class.java)) {
			throw WrongCredentialsException(cloud)
		}
	}

	private class Intercepted(cloud: DropboxCloud, context: Context) : CloudContentRepository<DropboxCloud, DropboxNode, DropboxFolder, DropboxFile> {

		private val cloud: DropboxImpl = DropboxImpl(cloud, context)

		override fun root(cloud: DropboxCloud): DropboxFolder {
			return this.cloud.root()
		}

		override fun resolve(cloud: DropboxCloud, path: String): DropboxFolder {
			return this.cloud.resolve(path)
		}

		override fun file(parent: DropboxFolder, name: String): DropboxFile {
			return cloud.file(parent, name, null)
		}

		@Throws(BackendException::class)
		override fun file(parent: DropboxFolder, name: String, size: Long?): DropboxFile {
			return cloud.file(parent, name, size)
		}

		override fun folder(parent: DropboxFolder, name: String): DropboxFolder {
			return cloud.folder(parent, name)
		}

		@Throws(BackendException::class)
		override fun exists(node: DropboxNode): Boolean {
			return try {
				cloud.exists(node)
			} catch (e: DbxException) {
				throw FatalBackendException(e)
			}
		}

		@Throws(BackendException::class)
		override fun list(folder: DropboxFolder): List<DropboxNode> {
			return try {
				cloud.list(folder)
			} catch (e: DbxException) {
				if (e is ListFolderErrorException) {
					if (e.errorValue.pathValue.isNotFound) {
						throw NoSuchCloudFileException()
					}
				}
				throw FatalBackendException(e)
			}
		}

		@Throws(BackendException::class)
		override fun create(folder: DropboxFolder): DropboxFolder {
			return try {
				cloud.create(folder)
			} catch (e: DbxException) {
				if (e is CreateFolderErrorException) {
					throw CloudNodeAlreadyExistsException(folder.name)
				}
				throw FatalBackendException(e)
			}
		}

		@Throws(BackendException::class)
		override fun move(source: DropboxFolder, target: DropboxFolder): DropboxFolder {
			return try {
				cloud.move(source, target) as DropboxFolder
			} catch (e: DbxException) {
				if (e is RelocationErrorException) {
					if (ExceptionUtil.extract(e, RelocationErrorException::class.java).get().errorValue.isFromLookup) {
						throw NoSuchCloudFileException(source.name)
					}
					throw CloudNodeAlreadyExistsException(target.name)
				}
				throw FatalBackendException(e)
			}
		}

		@Throws(BackendException::class)
		override fun move(source: DropboxFile, target: DropboxFile): DropboxFile {
			return try {
				cloud.move(source, target) as DropboxFile
			} catch (e: DbxException) {
				if (e is RelocationErrorException) {
					throw CloudNodeAlreadyExistsException(target.name)
				}
				throw FatalBackendException(e)
			}
		}

		@Throws(BackendException::class)
		override fun write(file: DropboxFile, data: DataSource, progressAware: ProgressAware<UploadState>, replace: Boolean, size: Long): DropboxFile {
			return try {
				cloud.write(file, data, progressAware, replace, size)
			} catch (e: IOException) {
				if (ExceptionUtil.contains(e, NoSuchCloudFileException::class.java)) {
					throw NoSuchCloudFileException(file.name)
				}
				throw FatalBackendException(e)
			} catch (e: DbxException) {
				if (ExceptionUtil.contains(e, NoSuchCloudFileException::class.java)) {
					throw NoSuchCloudFileException(file.name)
				}
				throw FatalBackendException(e)
			}
		}

		@Throws(BackendException::class)
		override fun read(file: DropboxFile, encryptedTmpFile: File?, data: OutputStream, progressAware: ProgressAware<DownloadState>) {
			try {
				cloud.read(file, encryptedTmpFile, data, progressAware)
			} catch (e: IOException) {
				mapToNoSuchCloudFileExceptionIfMatches(e, file)?.let { throw it } ?: throw FatalBackendException(e)
			} catch (e: DbxException) {
				mapToNoSuchCloudFileExceptionIfMatches(e, file)?.let { throw it } ?: throw FatalBackendException(e)
			}
		}

		private fun mapToNoSuchCloudFileExceptionIfMatches(e: Exception, file: DropboxFile) : NoSuchCloudFileException? {
			if (ExceptionUtil.contains(e, GetMetadataErrorException::class.java)) {
				if (ExceptionUtil.extract(e, GetMetadataErrorException::class.java).get().errorValue.pathValue.isNotFound) {
					return NoSuchCloudFileException(file.name)
				}
			}
			else if (ExceptionUtil.contains(e, DownloadErrorException::class.java)) {
				if (ExceptionUtil.extract(e, DownloadErrorException::class.java).get().errorValue.pathValue.isNotFound) {
					return NoSuchCloudFileException(file.name)
				}
			}
			return null
		}

		@Throws(BackendException::class)
		override fun delete(node: DropboxNode) {
			try {
				cloud.delete(node)
			} catch (e: DbxException) {
				if (ExceptionUtil.contains(e, DeleteErrorException::class.java)) {
					if (ExceptionUtil.extract(e, DeleteErrorException::class.java).get().errorValue.pathLookupValue.isNotFound) {
						throw NoSuchCloudFileException(node.name)
					}
				}
				throw FatalBackendException(e)
			}
		}

		@Throws(BackendException::class)
		override fun checkAuthenticationAndRetrieveCurrentAccount(cloud: DropboxCloud): String {
			return try {
				this.cloud.currentAccount()
			} catch (e: DbxException) {
				throw FatalBackendException(e)
			}
		}

		@Throws(BackendException::class)
		override fun logout(cloud: DropboxCloud) {
			// empty
		}

	}
}
