package org.cryptomator.data.cloud.smb

import android.content.Context
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import org.cryptomator.data.cloud.InterceptingCloudContentRepository
import org.cryptomator.domain.SmbCloud
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.exception.CloudNodeAlreadyExistsException
import org.cryptomator.domain.exception.NetworkConnectionException
import org.cryptomator.domain.exception.NoSuchCloudFileException
import org.cryptomator.domain.repository.CloudContentRepository
import org.cryptomator.domain.usecases.ProgressAware
import org.cryptomator.domain.usecases.cloud.DataSource
import org.cryptomator.domain.usecases.cloud.DownloadState
import org.cryptomator.domain.usecases.cloud.Progress
import org.cryptomator.domain.usecases.cloud.UploadState
import org.cryptomator.util.ExceptionUtil
import org.cryptomator.util.crypto.CredentialCryptor
import java.io.File
import java.io.OutputStream
import java.net.URI
import java.util.Date
import java.util.EnumSet

internal class SmbCloudContentRepository(
	cloud: SmbCloud,
	context: Context,
) : InterceptingCloudContentRepository<SmbCloud, SmbNode, SmbFolder, SmbFile>(Intercepted(cloud, context)) {

	@Throws(BackendException::class)
	override fun throwWrappedIfRequired(e: Exception) {
		val apiException = ExceptionUtil.extract(e, SMBApiException::class.java)
		if (apiException.isPresent) {
			val cause: SMBApiException = apiException.get()
			val status = cause.status.value
			if ((status == STATUS_OBJECT_PATH_NOT_FOUND) || (status == STATUS_OBJECT_NAME_NOT_FOUND)) {
				throw NoSuchCloudFileException(cause.message)
			}
			throw NetworkConnectionException(cause)
		}
		val smbException = ExceptionUtil.extract(e, com.hierynomus.smbj.common.SMBException::class.java)
		if (smbException.isPresent) {
			throw NetworkConnectionException(smbException.get())
		}
	}

	private class Intercepted(
		private val cloud: SmbCloud,
		private val context: Context
	) : CloudContentRepository<SmbCloud, SmbNode, SmbFolder, SmbFile> {

		private fun getDecryptedPassword(): String {
			return CredentialCryptor.getInstance(context).decrypt(cloud.password())
		}

		private fun normalizePath(path: String): String {
			return path.replace("/", "\\")
		}

		private fun parseSmbUrl(url: String): Triple<String, String, String> {
			val uri = try {
				URI(url)
			} catch (e: Exception) {
				throw IllegalArgumentException("Invalid SMB URL format", e)
			}
			val host = uri.host ?: throw IllegalArgumentException("Invalid host in SMB URL")
			val path = uri.path?.removePrefix("/") ?: ""
			val parts = path.split("/").filter { it.isNotEmpty() }
			val share = parts.firstOrNull() ?: throw IllegalArgumentException("Missing share name in SMB URL. Format: smb://hostname/sharename/")
			val basePath = parts.asSequence().drop(1).joinToString("/")
			return Triple(host, share, basePath)
		}

		/**
		 * Helper function to handle SMB client lifecycle.
		 * Manages connection, authentication, and share connection, ensuring all resources are closed correctly.
		 */
		private fun <T> withDiskShare(action: (DiskShare) -> T): T {
			val (host, share, _) = parseSmbUrl(cloud.url())
			return SMBClient().use { client ->
				client.connect(host).use { connection ->
					val authContext = AuthenticationContext(cloud.username(), getDecryptedPassword().toCharArray(), cloud.domain() ?: "")
					val session = connection.authenticate(authContext)
					session.use { s ->
						s.connectShare(share).use { ds ->
							if (ds is DiskShare) {
								action(ds)
							} else {
								throw RuntimeException("Specified share is not a disk share")
							}
						}
					}
				}
			}
		}

		override fun checkAuthenticationAndRetrieveCurrentAccount(cloud: SmbCloud): String {
			return withDiskShare { cloud.username() }
		}

		override fun root(cloud: SmbCloud): SmbFolder {
			val (_, _, basePath) = parseSmbUrl(cloud.url())
			return SmbFolder(null, basePath, cloud)
		}

		override fun resolve(cloud: SmbCloud, path: String): SmbFolder {
			return SmbFolder(null, path, cloud)
		}

		override fun file(parent: SmbFolder, name: String): SmbFile {
			return SmbFile(parent, name, cloud)
		}

		override fun file(parent: SmbFolder, name: String, size: Long?): SmbFile {
			return SmbFile(parent, name, cloud, size)
		}

		override fun folder(parent: SmbFolder, name: String): SmbFolder {
			return SmbFolder(parent, name, cloud)
		}

		override fun exists(node: SmbNode): Boolean {
			return try {
				withDiskShare { ds ->
					val normalizedPath = normalizePath(node.path)
					ds.fileExists(normalizedPath) || ds.folderExists(normalizedPath)
				}
			} catch (_: Exception) {
				false
			}
		}

		override fun list(folder: SmbFolder): List<SmbNode> {
			return withDiskShare { ds ->
				val normalizedPath = normalizePath(folder.path)
				ds.list(normalizedPath).mapNotNull { fileInfo ->
					if (fileInfo.fileName == "." || fileInfo.fileName == "..") {
						null
					} else {
						val isDirectory = (fileInfo.fileAttributes and ATTR_DIRECTORY) != 0L
						if (isDirectory) {
							SmbFolder(folder, fileInfo.fileName, cloud)
						} else {
							SmbFile(folder, fileInfo.fileName, cloud, fileInfo.endOfFile, Date(fileInfo.lastWriteTime.toEpochMillis()))
						}
					}
				}
			}
		}

		override fun create(folder: SmbFolder): SmbFolder {
			return withDiskShare { ds ->
				mkdirs(ds, normalizePath(folder.path))
				folder
			}
		}

		/**
		 * Recursively creates folders if they don't exist.
		 * SMBJ requires parent folders to exist before creating a subfolder.
		 */
		private fun mkdirs(ds: DiskShare, path: String) {
			val normalizedPath = path.replace("/", "\\")
			if (normalizedPath.isEmpty() || ds.folderExists(normalizedPath)) return

			val parent = if (normalizedPath.contains('\\')) normalizedPath.substringBeforeLast('\\') else ""
			if (parent.isNotEmpty()) {
				mkdirs(ds, parent)
			}

			if (!ds.folderExists(normalizedPath)) {
				ds.mkdir(normalizedPath)
			}
		}

		override fun move(source: SmbFolder, target: SmbFolder): SmbFolder {
			moveNode(source, target)
			return target
		}

		override fun move(source: SmbFile, target: SmbFile): SmbFile {
			moveNode(source, target)
			return target
		}

		/**
		 * SMBJ requires different open calls for files vs directories when performing a rename/move.
		 */
		private fun moveNode(source: SmbNode, target: SmbNode) {
			withDiskShare { ds ->
				val normalizedTargetPath = normalizePath(target.path)
				if (ds.fileExists(normalizedTargetPath) || ds.folderExists(normalizedTargetPath)) {
					throw CloudNodeAlreadyExistsException(target.name)
				}
				val normalizedSourcePath = normalizePath(source.path)
				val accessMask = EnumSet.of(AccessMask.DELETE, AccessMask.GENERIC_READ)
				val shareAccess = EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ, SMB2ShareAccess.FILE_SHARE_WRITE, SMB2ShareAccess.FILE_SHARE_DELETE)
				val createDisposition = SMB2CreateDisposition.FILE_OPEN
				if (ds.folderExists(normalizedSourcePath)) {
					ds.openDirectory(normalizedSourcePath, accessMask, null, shareAccess, createDisposition, null).use { directory ->
						directory.rename(normalizedTargetPath)
					}
				} else {
					ds.openFile(normalizedSourcePath, accessMask, null, shareAccess, createDisposition, null).use { file ->
						file.rename(normalizedTargetPath)
					}
				}
			}
		}

		override fun write(file: SmbFile, data: DataSource, progressAware: ProgressAware<UploadState>, replace: Boolean, size: Long): SmbFile {
			return withDiskShare { ds ->
				val normalizedPath = normalizePath(file.path)
				if (!replace && ds.fileExists(normalizedPath)) {
					throw CloudNodeAlreadyExistsException(file.name)
				}
				progressAware.onProgress(Progress.started(UploadState.upload(file)))
				val disposition = if (replace) SMB2CreateDisposition.FILE_OVERWRITE_IF else SMB2CreateDisposition.FILE_CREATE
				ds.openFile(normalizedPath, EnumSet.of(AccessMask.GENERIC_WRITE), null, SMB2ShareAccess.ALL, disposition, null).use { f ->
					f.outputStream.use { outputStream ->
						data.open(context)?.use { inputStream ->
							val buffer = ByteArray(8192)
							var bytesRead: Int
							var totalTransferred = 0L
							while (inputStream.read(buffer).also { bytesRead = it } != -1) {
								outputStream.write(buffer, 0, bytesRead)
								totalTransferred += bytesRead
								progressAware.onProgress(
									Progress.progress(UploadState.upload(file))
										.between(0)
										.and(size)
										.withValue(totalTransferred)
								)
							}
						}
					}
				}
				// Retrieve file info after upload
				val fileInfo = ds.getFileInformation(normalizedPath)
				SmbFile(file.parent, file.name, cloud, fileInfo.standardInformation.endOfFile, Date(fileInfo.basicInformation.lastWriteTime.toEpochMillis()))
			}
		}

		override fun read(file: SmbFile, encryptedTmpFile: File?, data: OutputStream, progressAware: ProgressAware<DownloadState>) {
			withDiskShare { ds ->
				val normalizedPath = normalizePath(file.path)
				ds.openFile(normalizedPath, EnumSet.of(AccessMask.FILE_READ_DATA), null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null).use { f ->
					f.inputStream.use { it.copyTo(data) }
				}
			}
		}

		override fun delete(node: SmbNode) {
			withDiskShare { ds ->
				val normalizedPath = normalizePath(node.path)
				if (ds.folderExists(normalizedPath)) {
					deleteRecursive(ds, normalizedPath)
				} else if (ds.fileExists(normalizedPath) || ds.folderExists(normalizedPath)) {
					ds.rm(normalizedPath)
				}
			}
		}

		/**
		 * Recursively deletes a directory and its contents.
		 * SMBJ doesn't provide a native recursive delete.
		 */
		private fun deleteRecursive(ds: DiskShare, path: String) {
			ds.list(path).forEach { fileInfo ->
				val name = fileInfo.fileName
				if (name != "." && name != "..") {
					val childPath = "$path\\$name"
					if ((fileInfo.fileAttributes and ATTR_DIRECTORY) != 0L) {
						deleteRecursive(ds, childPath)
					} else {
						ds.rm(childPath)
					}
				}
			}
			ds.rmdir(path, false)
		}

		override fun logout(cloud: SmbCloud) {
			// No-op
		}
	}

	companion object {
		private const val ATTR_DIRECTORY = 0x10L
		private const val STATUS_OBJECT_PATH_NOT_FOUND = 0xc000003aL
		private const val STATUS_OBJECT_NAME_NOT_FOUND = 0xc0000034L
	}
}

