package org.cryptomator.data.cloud.local.file

import android.content.Context
import org.cryptomator.domain.LocalStorageCloud
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.domain.exception.NoSuchCloudFileException
import org.cryptomator.domain.repository.CloudContentRepository
import org.cryptomator.domain.usecases.ProgressAware
import org.cryptomator.domain.usecases.cloud.DataSource
import org.cryptomator.domain.usecases.cloud.DownloadState
import org.cryptomator.domain.usecases.cloud.UploadState
import org.cryptomator.util.ExceptionUtil
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream

class LocalStorageContentRepository(context: Context, localStorageCloud: LocalStorageCloud) : CloudContentRepository<LocalStorageCloud, LocalNode, LocalFolder, LocalFile> {

	private val localStorageImpl: LocalStorageImpl = LocalStorageImpl(context, localStorageCloud)

	@Throws(BackendException::class)
	override fun root(cloud: LocalStorageCloud): LocalFolder {
		return localStorageImpl.root()
	}

	override fun resolve(cloud: LocalStorageCloud, path: String): LocalFolder {
		return localStorageImpl.resolve(path)
	}

	@Throws(BackendException::class)
	override fun file(parent: LocalFolder, name: String): LocalFile {
		return localStorageImpl.file(parent, name, null)
	}

	@Throws(BackendException::class)
	override fun file(parent: LocalFolder, name: String, size: Long?): LocalFile {
		return localStorageImpl.file(parent, name, size)
	}

	@Throws(BackendException::class)
	override fun folder(parent: LocalFolder, name: String): LocalFolder {
		return localStorageImpl.folder(parent, name)
	}

	@Throws(BackendException::class)
	override fun exists(node: LocalNode): Boolean {
		return localStorageImpl.exists(node)
	}

	@Throws(BackendException::class)
	override fun list(folder: LocalFolder): List<LocalNode> {
		return localStorageImpl.list(folder)
	}

	@Throws(BackendException::class)
	override fun create(folder: LocalFolder): LocalFolder {
		return localStorageImpl.create(folder)
	}

	@Throws(BackendException::class)
	override fun move(source: LocalFolder, target: LocalFolder): LocalFolder {
		return localStorageImpl.move(source, target) as LocalFolder
	}

	@Throws(BackendException::class)
	override fun move(source: LocalFile, target: LocalFile): LocalFile {
		return localStorageImpl.move(source, target) as LocalFile
	}

	@Throws(BackendException::class)
	override fun write(file: LocalFile, data: DataSource, progressAware: ProgressAware<UploadState>, replace: Boolean, size: Long): LocalFile {
		return try {
			localStorageImpl.write(file, data, progressAware, replace, size)
		} catch (e: IOException) {
			if (ExceptionUtil.contains(e, FileNotFoundException::class.java)) {
				throw NoSuchCloudFileException(file.name)
			}
			throw FatalBackendException(e)
		}
	}

	@Throws(BackendException::class)
	override fun read(file: LocalFile, encryptedTmpFile: File?, data: OutputStream, progressAware: ProgressAware<DownloadState>) {
		try {
			localStorageImpl.read(file, data, progressAware)
		} catch (e: IOException) {
			if (ExceptionUtil.contains(e, FileNotFoundException::class.java)) {
				throw NoSuchCloudFileException(file.name)
			}
			throw FatalBackendException(e)
		}
	}

	@Throws(BackendException::class)
	override fun delete(node: LocalNode) {
		localStorageImpl.delete(node)
	}

	@Throws(BackendException::class)
	override fun checkAuthenticationAndRetrieveCurrentAccount(cloud: LocalStorageCloud): String {
		return ""
	}

	@Throws(BackendException::class)
	override fun logout(cloud: LocalStorageCloud) {
		// empty
	}

}
