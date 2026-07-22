package org.cryptomator.data.cloud.smb

import android.content.Context
import org.cryptomator.data.cloud.InterceptingCloudContentRepository
import org.cryptomator.domain.SmbCloud
import org.cryptomator.domain.repository.CloudContentRepository
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.exception.NetworkConnectionException
import org.cryptomator.domain.usecases.ProgressAware
import org.cryptomator.domain.usecases.cloud.DataSource
import org.cryptomator.domain.usecases.cloud.DownloadState
import org.cryptomator.domain.usecases.cloud.UploadState
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import org.cryptomator.util.crypto.CredentialCryptor
import timber.log.Timber

/**
 * SMB Cloud content repository implementation.
 */
internal class SmbCloudContentRepository(
	private val cloud: SmbCloud,
	private val context: Context
) : InterceptingCloudContentRepository<SmbCloud, SmbNode, SmbFolder, SmbFile>(Intercepted(cloud, context)) {

	@Throws(BackendException::class)
	override fun throwWrappedIfRequired(e: Exception) {
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

		private fun parseSmbUrl(url: String): Pair<String, String> {
			// Expected format: smb://host/share/
			val uri = try {
				URI(url)
			} catch (e: Exception) {
				throw IllegalArgumentException("Invalid SMB URL format", e)
			}
			val host = uri.host ?: throw IllegalArgumentException("Invalid host in SMB URL")
			val path = uri.path ?: ""
			val share = path.split("/").filter { it.isNotEmpty() }.firstOrNull()
				?: throw IllegalArgumentException("Missing share name in SMB URL. Format: smb://hostname/sharename/")
			return Pair(host, share)
		}

		override fun checkAuthenticationAndRetrieveCurrentAccount(cloud: SmbCloud): String {
			val (host, share) = parseSmbUrl(cloud.url())
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
			return SmbFolder(null, "", cloud)
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
			throw UnsupportedOperationException("SMB not yet implemented")
		}

		override fun list(folder: SmbFolder): List<SmbNode> {
			return emptyList()
		}

		override fun create(folder: SmbFolder): SmbFolder {
			throw UnsupportedOperationException("SMB not yet implemented")
		}

		override fun move(source: SmbFolder, target: SmbFolder): SmbFolder {
			throw UnsupportedOperationException("SMB not yet implemented")
		}

		override fun move(source: SmbFile, target: SmbFile): SmbFile {
			throw UnsupportedOperationException("SMB not yet implemented")
		}

		override fun write(file: SmbFile, data: DataSource, progressAware: ProgressAware<UploadState>, replace: Boolean, size: Long): SmbFile {
			throw UnsupportedOperationException("SMB not yet implemented")
		}

		override fun read(file: SmbFile, encryptedTmpFile: File?, data: OutputStream, progressAware: ProgressAware<DownloadState>) {
			throw UnsupportedOperationException("SMB not yet implemented")
		}

		override fun delete(node: SmbNode) {
			throw UnsupportedOperationException("SMB not yet implemented")
		}

		override fun logout(cloud: SmbCloud) {
			// No-op for now
		}
	}
}
