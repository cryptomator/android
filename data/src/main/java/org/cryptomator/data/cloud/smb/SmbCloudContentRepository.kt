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
import org.cryptomator.util.crypto.CredentialCryptor
import timber.log.Timber
import java.io.File
import java.io.OutputStream
import java.net.URI
import java.util.Date
import java.util.EnumSet

/**
 * SMB Cloud content repository implementation.
 */
internal class SmbCloudContentRepository(
	private val cloud: SmbCloud,
	private val context: Context
) : InterceptingCloudContentRepository<SmbCloud, SmbNode, SmbFolder, SmbFile>(Intercepted(cloud, context)) {

	@Throws(BackendException::class)
	override fun throwWrappedIfRequired(e: Exception) {
		if (e is SMBApiException) {
			val status = e.status.value
			if (status == 0xc000003aL || status == 0xc0000034L) { // STATUS_OBJECT_PATH_NOT_FOUND or STATUS_OBJECT_NAME_NOT_FOUND
				throw NoSuchCloudFileException(e.message)
			}
		}
		if (e is com.hierynomus.smbj.common.SMBException) {
			throw NetworkConnectionException(e)
		}
	}

	private class Intercepted(
		private val cloud: SmbCloud,
		private val context: Context
	) : CloudContentRepository<SmbCloud, SmbNode, SmbFolder, SmbFile> {

		private fun getDecryptedPassword(): String {
			return CredentialCryptor.getInstance(context).decrypt(cloud.password())
		}

		private fun parseSmbUrl(url: String): Triple<String, String, String> {
			// Expected format: smb://host/share/optional/path
			val uri = try {
				URI(url)
			} catch (e: Exception) {
				throw IllegalArgumentException("Invalid SMB URL format", e)
			}
			val host = uri.host ?: throw IllegalArgumentException("Invalid host in SMB URL")
			val path = uri.path?.removePrefix("/") ?: ""
			val parts = path.split("/").filter { it.isNotEmpty() }
			val share = parts.firstOrNull() ?: throw IllegalArgumentException("Missing share name in SMB URL. Format: smb://hostname/sharename/")
			val basePath = parts.drop(1).joinToString("/")
			return Triple(host, share, basePath)
		}

		override fun checkAuthenticationAndRetrieveCurrentAccount(cloud: SmbCloud): String {
			val (host, share, _) = parseSmbUrl(cloud.url())
			val client = SMBClient()
			try {
				client.connect(host).use { connection ->
					val authContext = AuthenticationContext(cloud.username(), getDecryptedPassword().toCharArray(), cloud.domain() ?: "")
					val session = connection.authenticate(authContext)
					session.use { s ->
						s.connectShare(share).use { ds ->
							if (ds is DiskShare) {
								return cloud.username()
							} else {
								throw NetworkConnectionException(RuntimeException("Specified share is not a disk share"))
							}
						}
					}
				}
			} catch (e: Exception) {
				Timber.tag("SmbContentRepo").e(e, "SMB Authentication failed")
				throw NetworkConnectionException(e)
			}
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
			val (host, share, _) = parseSmbUrl(cloud.url())
			val client = SMBClient()
			try {
				client.connect(host).use { connection ->
					val authContext = AuthenticationContext(cloud.username(), getDecryptedPassword().toCharArray(), cloud.domain() ?: "")
					val session = connection.authenticate(authContext)
					session.use { s ->
						s.connectShare(share).use { ds ->
							if (ds is DiskShare) {
								return ds.fileExists(node.path) || ds.folderExists(node.path)
							}
							return false
						}
					}
				}
			} catch (e: Exception) {
				return false
			}
		}

		override fun list(folder: SmbFolder): List<SmbNode> {
			val (host, share, _) = parseSmbUrl(cloud.url())
			val client = SMBClient()
			try {
				client.connect(host).use { connection ->
					val authContext = AuthenticationContext(cloud.username(), getDecryptedPassword().toCharArray(), cloud.domain() ?: "")
					val session = connection.authenticate(authContext)
					session.use { s ->
						s.connectShare(share).use { ds ->
							if (ds is DiskShare) {
								return ds.list(folder.path).map { fileInfo ->
									if (fileInfo.fileName == "." || fileInfo.fileName == "..") {
										null
									} else {
										val isDirectory = (fileInfo.fileAttributes and 0x10L) != 0L
										if (isDirectory) {
											SmbFolder(folder, fileInfo.fileName, cloud)
										} else {
											SmbFile(folder, fileInfo.fileName, cloud, fileInfo.endOfFile, Date(fileInfo.lastWriteTime.toEpochMillis()))
										}
									}
								}.filterNotNull()
							} else {
								throw NetworkConnectionException(RuntimeException("Specified share is not a disk share"))
							}
						}
					}
				}
			} catch (e: Exception) {
				Timber.tag("SmbContentRepo").e(e, "SMB Listing failed for path: ${folder.path}")
				throw NetworkConnectionException(e)
			}
		}

		override fun create(folder: SmbFolder): SmbFolder {
			val (host, share, _) = parseSmbUrl(cloud.url())
			val client = SMBClient()
			try {
				client.connect(host).use { connection ->
					val authContext = AuthenticationContext(cloud.username(), getDecryptedPassword().toCharArray(), cloud.domain() ?: "")
					val session = connection.authenticate(authContext)
					session.use { s ->
						s.connectShare(share).use { ds ->
							if (ds is DiskShare) {
								if (!ds.folderExists(folder.path)) {
									ds.mkdir(folder.path)
								}
								return folder
							} else {
								throw NetworkConnectionException(RuntimeException("Specified share is not a disk share"))
							}
						}
					}
				}
			} catch (e: Exception) {
				Timber.tag("SmbContentRepo").e(e, "SMB Create folder failed for path: ${folder.path}")
				throw NetworkConnectionException(e)
			}
		}

		override fun move(source: SmbFolder, target: SmbFolder): SmbFolder {
			throw UnsupportedOperationException("SMB move not yet implemented")
		}

		override fun move(source: SmbFile, target: SmbFile): SmbFile {
			throw UnsupportedOperationException("SMB move not yet implemented")
		}

		override fun write(file: SmbFile, data: DataSource, progressAware: ProgressAware<UploadState>, replace: Boolean, size: Long): SmbFile {
			val (host, share, _) = parseSmbUrl(cloud.url())
			val client = SMBClient()
			try {
				client.connect(host).use { connection ->
					val authContext = AuthenticationContext(cloud.username(), getDecryptedPassword().toCharArray(), cloud.domain() ?: "")
					val session = connection.authenticate(authContext)
					session.use { s ->
						s.connectShare(share).use { ds ->
							if (ds is DiskShare) {
								if (!replace && ds.fileExists(file.path)) {
									throw CloudNodeAlreadyExistsException(file.name)
								}
								progressAware.onProgress(Progress.started(UploadState.upload(file)))
								val disposition = if (replace) SMB2CreateDisposition.FILE_OVERWRITE_IF else SMB2CreateDisposition.FILE_CREATE
								ds.openFile(file.path, EnumSet.of(AccessMask.GENERIC_WRITE), null, SMB2ShareAccess.ALL, disposition, null).use { f ->
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
								val fileInfo = ds.getFileInformation(file.path)
								return SmbFile(file.parent, file.name, cloud, fileInfo.standardInformation.endOfFile, Date(fileInfo.basicInformation.lastWriteTime.toEpochMillis()))
							} else {
								throw NetworkConnectionException(RuntimeException("Specified share is not a disk share"))
							}
						}
					}
				}
			} catch (e: Exception) {
				Timber.tag("SmbContentRepo").e(e, "SMB Write failed for path: ${file.path}")
				throw NetworkConnectionException(e)
			}
		}

		override fun read(file: SmbFile, encryptedTmpFile: File?, data: OutputStream, progressAware: ProgressAware<DownloadState>) {
			val (host, share, _) = parseSmbUrl(cloud.url())
			val client = SMBClient()
			try {
				client.connect(host).use { connection ->
					val authContext = AuthenticationContext(cloud.username(), getDecryptedPassword().toCharArray(), cloud.domain() ?: "")
					val session = connection.authenticate(authContext)
					session.use { s ->
						s.connectShare(share).use { ds ->
							if (ds is DiskShare) {
								ds.openFile(file.path, EnumSet.of(AccessMask.FILE_READ_DATA), null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null).use { f ->
									f.inputStream.use { it.copyTo(data) }
								}
							} else {
								throw NetworkConnectionException(RuntimeException("Specified share is not a disk share"))
							}
						}
					}
				}
			} catch (e: Exception) {
				Timber.tag("SmbContentRepo").e(e, "SMB Read failed for path: ${file.path}")
				throw NetworkConnectionException(e)
			}
		}

		override fun delete(node: SmbNode) {
			val (host, share, _) = parseSmbUrl(cloud.url())
			val client = SMBClient()
			try {
				client.connect(host).use { connection ->
					val authContext = AuthenticationContext(cloud.username(), getDecryptedPassword().toCharArray(), cloud.domain() ?: "")
					val session = connection.authenticate(authContext)
					session.use { s ->
						s.connectShare(share).use { ds ->
							if (ds is DiskShare) {
								if (ds.folderExists(node.path)) {
									ds.rmdir(node.path, true)
								} else if (ds.fileExists(node.path)) {
									ds.rm(node.path)
								}
							} else {
								throw NetworkConnectionException(RuntimeException("Specified share is not a disk share"))
							}
						}
					}
				}
			} catch (e: Exception) {
				Timber.tag("SmbContentRepo").e(e, "SMB Delete failed for path: ${node.path}")
				throw NetworkConnectionException(e)
			}
		}

		override fun logout(cloud: SmbCloud) {
			// No-op
		}
	}
}
