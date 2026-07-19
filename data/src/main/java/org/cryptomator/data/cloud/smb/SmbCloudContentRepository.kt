package org.cryptomator.data.cloud.smb

import android.content.Context
import org.cryptomator.data.cloud.InterceptingCloudContentRepository
import org.cryptomator.domain.SmbCloud
import org.cryptomator.domain.repository.CloudContentRepository
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.usecases.ProgressAware
import org.cryptomator.domain.usecases.cloud.DataSource
import org.cryptomator.domain.usecases.cloud.DownloadState
import org.cryptomator.domain.usecases.cloud.UploadState
import java.io.File
import java.io.OutputStream

/**
 * SMB Cloud content repository implementation.
 * Skeleton for the first step of SMB support.
 */
internal class SmbCloudContentRepository(
	private val cloud: SmbCloud,
	context: Context
) : InterceptingCloudContentRepository<SmbCloud, SmbNode, SmbFolder, SmbFile>(Intercepted(cloud)) {

	@Throws(BackendException::class)
	override fun throwWrappedIfRequired(e: Exception) {
		// Not yet implemented
	}

	private class Intercepted(private val cloud: SmbCloud) : CloudContentRepository<SmbCloud, SmbNode, SmbFolder, SmbFile> {

		override fun root(cloud: SmbCloud): SmbFolder {
			throw UnsupportedOperationException("SMB not yet implemented")
		}

		override fun resolve(cloud: SmbCloud, path: String): SmbFolder {
			throw UnsupportedOperationException("SMB not yet implemented")
		}

		override fun file(parent: SmbFolder, name: String): SmbFile {
			throw UnsupportedOperationException("SMB not yet implemented")
		}

		override fun file(parent: SmbFolder, name: String, size: Long?): SmbFile {
			throw UnsupportedOperationException("SMB not yet implemented")
		}

		override fun folder(parent: SmbFolder, name: String): SmbFolder {
			throw UnsupportedOperationException("SMB not yet implemented")
		}

		override fun exists(node: SmbNode): Boolean {
			throw UnsupportedOperationException("SMB not yet implemented")
		}

		override fun list(folder: SmbFolder): List<SmbNode> {
			throw UnsupportedOperationException("SMB not yet implemented")
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

		override fun checkAuthenticationAndRetrieveCurrentAccount(cloud: SmbCloud): String {
			throw UnsupportedOperationException("SMB not yet implemented")
		}

		override fun logout(cloud: SmbCloud) {
			throw UnsupportedOperationException("SMB not yet implemented")
		}
	}
}
