package org.cryptomator.data.cloud.smb

import android.content.Context
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.SmbCloud
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.repository.CloudContentRepository
import org.cryptomator.domain.usecases.ProgressAware
import org.cryptomator.domain.usecases.cloud.DataSource
import org.cryptomator.domain.usecases.cloud.DownloadState
import org.cryptomator.domain.usecases.cloud.UploadState
import java.io.File
import java.io.OutputStream

internal class SmbCloudContentRepository(cloud: SmbCloud, private val connectionPool: SmbConnectionPool, private val context: Context) : CloudContentRepository<SmbCloud, SmbNode, SmbFolder, SmbFile> {

	private var smbImpl = SmbImpl(cloud, connectionPool, context)

	/**
	 * The repository is cached per cloud ID, so edited connection settings only arrive with the calls.
	 */
	@Synchronized
	private fun implFor(cloud: Cloud?): SmbImpl {
		if (cloud is SmbCloud && !smbImpl.isFor(cloud)) {
			// closes the replaced connection as soon as no operation uses it anymore
			smbImpl.logout()
			smbImpl = SmbImpl(cloud, connectionPool, context)
		}
		return smbImpl
	}

	override fun root(cloud: SmbCloud): SmbFolder {
		return implFor(cloud).root()
	}

	override fun resolve(cloud: SmbCloud, path: String): SmbFolder {
		return implFor(cloud).resolve(path)
	}

	override fun file(parent: SmbFolder, name: String): SmbFile {
		return implFor(parent.cloud).file(parent, name, null)
	}

	override fun file(parent: SmbFolder, name: String, size: Long?): SmbFile {
		return implFor(parent.cloud).file(parent, name, size)
	}

	override fun folder(parent: SmbFolder, name: String): SmbFolder {
		return implFor(parent.cloud).folder(parent, name)
	}

	@Throws(BackendException::class)
	override fun exists(node: SmbNode): Boolean {
		return implFor(node.cloud).exists(node)
	}

	@Throws(BackendException::class)
	override fun list(folder: SmbFolder): List<SmbNode> {
		return implFor(folder.cloud).list(folder)
	}

	@Throws(BackendException::class)
	override fun create(folder: SmbFolder): SmbFolder {
		return implFor(folder.cloud).create(folder)
	}

	@Throws(BackendException::class)
	override fun move(source: SmbFolder, target: SmbFolder): SmbFolder {
		return implFor(source.cloud).move(source, target)
	}

	@Throws(BackendException::class)
	override fun move(source: SmbFile, target: SmbFile): SmbFile {
		return implFor(source.cloud).move(source, target)
	}

	@Throws(BackendException::class)
	override fun write(file: SmbFile, data: DataSource, progressAware: ProgressAware<UploadState>, replace: Boolean, size: Long): SmbFile {
		return implFor(file.cloud).write(file, data, progressAware, replace, size)
	}

	@Throws(BackendException::class)
	override fun read(file: SmbFile, encryptedTmpFile: File?, data: OutputStream, progressAware: ProgressAware<DownloadState>) {
		implFor(file.cloud).read(file, data, progressAware)
	}

	@Throws(BackendException::class)
	override fun delete(node: SmbNode) {
		implFor(node.cloud).delete(node)
	}

	@Throws(BackendException::class)
	override fun checkAuthenticationAndRetrieveCurrentAccount(cloud: SmbCloud): String {
		return implFor(cloud).currentAccount()
	}

	override fun logout(cloud: SmbCloud) {
		implFor(cloud).logout()
	}
}
