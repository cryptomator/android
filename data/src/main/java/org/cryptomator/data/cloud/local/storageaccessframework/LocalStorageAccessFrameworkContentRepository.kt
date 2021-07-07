package org.cryptomator.data.cloud.local.storageaccessframework

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
import org.cryptomator.util.file.MimeTypes
import java.io.File
import java.io.IOException
import java.io.OutputStream

class LocalStorageAccessFrameworkContentRepository(context: Context, mimeTypes: MimeTypes, localStorageCloud: LocalStorageCloud) :
	CloudContentRepository<LocalStorageCloud, LocalStorageAccessNode, LocalStorageAccessFolder, LocalStorageAccessFile> {

	private val localStorageAccessFramework: LocalStorageAccessFrameworkImpl = LocalStorageAccessFrameworkImpl(context, mimeTypes, localStorageCloud, DocumentIdCache())

	@Throws(BackendException::class)
	override fun root(cloud: LocalStorageCloud): LocalStorageAccessFolder {
		return localStorageAccessFramework.root()
	}

	override fun resolve(cloud: LocalStorageCloud, path: String): LocalStorageAccessFolder {
		return localStorageAccessFramework.resolve(path)
	}

	@Throws(BackendException::class)
	override fun file(parent: LocalStorageAccessFolder, name: String): LocalStorageAccessFile {
		return localStorageAccessFramework.file(parent, name, null)
	}

	@Throws(BackendException::class)
	override fun file(parent: LocalStorageAccessFolder, name: String, size: Long?): LocalStorageAccessFile {
		return localStorageAccessFramework.file(parent, name, size)
	}

	@Throws(BackendException::class)
	override fun folder(parent: LocalStorageAccessFolder, name: String): LocalStorageAccessFolder {
		return localStorageAccessFramework.folder(parent, name)
	}

	@Throws(BackendException::class)
	override fun exists(node: LocalStorageAccessNode): Boolean {
		return localStorageAccessFramework.exists(node)
	}

	@Throws(BackendException::class)
	override fun list(folder: LocalStorageAccessFolder): List<LocalStorageAccessNode> {
		return localStorageAccessFramework.list(folder)
	}

	@Throws(BackendException::class)
	override fun create(folder: LocalStorageAccessFolder): LocalStorageAccessFolder {
		return localStorageAccessFramework.create(folder)
	}

	@Throws(BackendException::class)
	override fun move(source: LocalStorageAccessFolder, target: LocalStorageAccessFolder): LocalStorageAccessFolder {
		if (source.documentId == null) {
			throw NoSuchCloudFileException(source.name)
		}
		return localStorageAccessFramework.move(source, target) as LocalStorageAccessFolder
	}

	@Throws(BackendException::class)
	override fun move(source: LocalStorageAccessFile, target: LocalStorageAccessFile): LocalStorageAccessFile {
		return localStorageAccessFramework.move(source, target) as LocalStorageAccessFile
	}

	@Throws(BackendException::class)
	override fun write(file: LocalStorageAccessFile, data: DataSource, progressAware: ProgressAware<UploadState>, replace: Boolean, size: Long): LocalStorageAccessFile {
		return try {
			localStorageAccessFramework.write(file, data, progressAware, replace, size)
		} catch (e: IOException) {
			throw FatalBackendException(e)
		}
	}

	@Throws(BackendException::class)
	override fun read(file: LocalStorageAccessFile, encryptedTmpFile: File?, data: OutputStream, progressAware: ProgressAware<DownloadState>) {
		try {
			if (file.documentId == null) {
				throw NoSuchCloudFileException(file.name)
			}
			localStorageAccessFramework.read(file, data, progressAware)
		} catch (e: IOException) {
			throw FatalBackendException(e)
		}
	}

	@Throws(BackendException::class)
	override fun delete(node: LocalStorageAccessNode) {
		localStorageAccessFramework.delete(node)
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
