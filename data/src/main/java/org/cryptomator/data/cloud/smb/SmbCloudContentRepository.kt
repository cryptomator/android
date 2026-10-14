package org.cryptomator.data.cloud.smb

import android.content.Context
import org.cryptomator.domain.SmbCloud
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.repository.CloudContentRepository
import org.cryptomator.domain.usecases.ProgressAware
import org.cryptomator.domain.usecases.cloud.DataSource
import org.cryptomator.domain.usecases.cloud.DownloadState
import org.cryptomator.domain.usecases.cloud.UploadState
import java.io.File
import java.io.OutputStream

internal class SmbCloudContentRepository(cloud: SmbCloud, connectionPool: SmbConnectionPool, context: Context) : CloudContentRepository<SmbCloud, SmbNode, SmbFolder, SmbFile> {

	private val smbImpl = SmbImpl(cloud, connectionPool, context)

	override fun root(cloud: SmbCloud): SmbFolder {
		return smbImpl.root()
	}

	override fun resolve(cloud: SmbCloud, path: String): SmbFolder {
		return smbImpl.resolve(path)
	}

	override fun file(parent: SmbFolder, name: String): SmbFile {
		return smbImpl.file(parent, name, null)
	}

	override fun file(parent: SmbFolder, name: String, size: Long?): SmbFile {
		return smbImpl.file(parent, name, size)
	}

	override fun folder(parent: SmbFolder, name: String): SmbFolder {
		return smbImpl.folder(parent, name)
	}

	@Throws(BackendException::class)
	override fun exists(node: SmbNode): Boolean {
		return smbImpl.exists(node)
	}

	@Throws(BackendException::class)
	override fun list(folder: SmbFolder): List<SmbNode> {
		return smbImpl.list(folder)
	}

	@Throws(BackendException::class)
	override fun create(folder: SmbFolder): SmbFolder {
		return smbImpl.create(folder)
	}

	@Throws(BackendException::class)
	override fun move(source: SmbFolder, target: SmbFolder): SmbFolder {
		return smbImpl.move(source, target)
	}

	@Throws(BackendException::class)
	override fun move(source: SmbFile, target: SmbFile): SmbFile {
		return smbImpl.move(source, target)
	}

	@Throws(BackendException::class)
	override fun write(file: SmbFile, data: DataSource, progressAware: ProgressAware<UploadState>, replace: Boolean, size: Long): SmbFile {
		return smbImpl.write(file, data, progressAware, replace, size)
	}

	@Throws(BackendException::class)
	override fun read(file: SmbFile, encryptedTmpFile: File?, data: OutputStream, progressAware: ProgressAware<DownloadState>) {
		smbImpl.read(file, data, progressAware)
	}

	@Throws(BackendException::class)
	override fun delete(node: SmbNode) {
		smbImpl.delete(node)
	}

	@Throws(BackendException::class)
	override fun checkAuthenticationAndRetrieveCurrentAccount(cloud: SmbCloud): String {
		return smbImpl.currentAccount()
	}

	override fun logout(cloud: SmbCloud) {
		smbImpl.logout()
	}
}
