package org.cryptomator.data.cloud

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.CloudFolder
import org.cryptomator.domain.CloudNode
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.repository.CloudContentRepository
import org.cryptomator.domain.usecases.ProgressAware
import org.cryptomator.domain.usecases.cloud.DataSource
import org.cryptomator.domain.usecases.cloud.DownloadState
import org.cryptomator.domain.usecases.cloud.UploadState
import java.io.File
import java.io.OutputStream

abstract class InterceptingCloudContentRepository<CloudType : Cloud, NodeType : CloudNode, DirType : CloudFolder, FileType : CloudFile> protected constructor(private val delegate: CloudContentRepository<CloudType, NodeType, DirType, FileType>) :
	CloudContentRepository<CloudType, NodeType, DirType, FileType> {

	@Throws(BackendException::class)
	protected abstract fun throwWrappedIfRequired(e: Exception)

	@Throws(BackendException::class)
	override fun root(cloud: CloudType): DirType {
		return try {
			delegate.root(cloud)
		} catch (e: BackendException) {
			throwWrappedIfRequired(e)
			throw e
		} catch (e: RuntimeException) {
			throwWrappedIfRequired(e)
			throw e
		}
	}

	@Throws(BackendException::class)
	override fun resolve(cloud: CloudType, path: String): DirType {
		return try {
			delegate.resolve(cloud, path)
		} catch (e: BackendException) {
			throwWrappedIfRequired(e)
			throw e
		} catch (e: RuntimeException) {
			throwWrappedIfRequired(e)
			throw e
		}
	}

	@Throws(BackendException::class)
	override fun file(parent: DirType, name: String): FileType {
		return try {
			delegate.file(parent, name)
		} catch (e: BackendException) {
			throwWrappedIfRequired(e)
			throw e
		} catch (e: RuntimeException) {
			throwWrappedIfRequired(e)
			throw e
		}
	}

	@Throws(BackendException::class)
	override fun file(parent: DirType, name: String, size: Long?): FileType {
		return try {
			delegate.file(parent, name, size)
		} catch (e: BackendException) {
			throwWrappedIfRequired(e)
			throw e
		} catch (e: RuntimeException) {
			throwWrappedIfRequired(e)
			throw e
		}
	}

	@Throws(BackendException::class)
	override fun folder(parent: DirType, name: String): DirType {
		return try {
			delegate.folder(parent, name)
		} catch (e: BackendException) {
			throwWrappedIfRequired(e)
			throw e
		} catch (e: RuntimeException) {
			throwWrappedIfRequired(e)
			throw e
		}
	}

	@Throws(BackendException::class)
	override fun exists(node: NodeType): Boolean {
		return try {
			delegate.exists(node)
		} catch (e: BackendException) {
			throwWrappedIfRequired(e)
			throw e
		} catch (e: RuntimeException) {
			throwWrappedIfRequired(e)
			throw e
		}
	}

	@Throws(BackendException::class)
	override fun list(folder: DirType): List<NodeType> {
		return try {
			delegate.list(folder)
		} catch (e: BackendException) {
			throwWrappedIfRequired(e)
			throw e
		} catch (e: RuntimeException) {
			throwWrappedIfRequired(e)
			throw e
		}
	}

	@Throws(BackendException::class)
	override fun create(folder: DirType): DirType {
		return try {
			delegate.create(folder)
		} catch (e: BackendException) {
			throwWrappedIfRequired(e)
			throw e
		} catch (e: RuntimeException) {
			throwWrappedIfRequired(e)
			throw e
		}
	}

	@Throws(BackendException::class)
	override fun move(source: DirType, target: DirType): DirType {
		return try {
			delegate.move(source, target)
		} catch (e: BackendException) {
			throwWrappedIfRequired(e)
			throw e
		} catch (e: RuntimeException) {
			throwWrappedIfRequired(e)
			throw e
		}
	}

	@Throws(BackendException::class)
	override fun move(source: FileType, target: FileType): FileType {
		return try {
			delegate.move(source, target)
		} catch (e: BackendException) {
			throwWrappedIfRequired(e)
			throw e
		} catch (e: RuntimeException) {
			throwWrappedIfRequired(e)
			throw e
		}
	}

	@Throws(BackendException::class)
	override fun write(file: FileType, data: DataSource, progressAware: ProgressAware<UploadState>, replace: Boolean, size: Long): FileType {
		return try {
			delegate.write(file, data, progressAware, replace, size)
		} catch (e: BackendException) {
			throwWrappedIfRequired(e)
			throw e
		} catch (e: RuntimeException) {
			throwWrappedIfRequired(e)
			throw e
		}
	}

	@Throws(BackendException::class)
	override fun read(file: FileType, encryptedTmpFile: File?, data: OutputStream, progressAware: ProgressAware<DownloadState>) {
		try {
			delegate.read(file, encryptedTmpFile, data, progressAware)
		} catch (e: BackendException) {
			throwWrappedIfRequired(e)
			throw e
		} catch (e: RuntimeException) {
			throwWrappedIfRequired(e)
			throw e
		}
	}

	@Throws(BackendException::class)
	override fun delete(node: NodeType) {
		try {
			delegate.delete(node)
		} catch (e: BackendException) {
			throwWrappedIfRequired(e)
			throw e
		} catch (e: RuntimeException) {
			throwWrappedIfRequired(e)
			throw e
		}
	}

	@Throws(BackendException::class)
	override fun checkAuthenticationAndRetrieveCurrentAccount(cloud: CloudType): String {
		return try {
			delegate.checkAuthenticationAndRetrieveCurrentAccount(cloud)
		} catch (e: BackendException) {
			throwWrappedIfRequired(e)
			throw e
		} catch (e: RuntimeException) {
			throwWrappedIfRequired(e)
			throw e
		}
	}

	@Throws(BackendException::class)
	override fun logout(cloud: CloudType) {
		try {
			delegate.logout(cloud)
		} catch (e: BackendException) {
			throwWrappedIfRequired(e)
			throw e
		} catch (e: RuntimeException) {
			throwWrappedIfRequired(e)
			throw e
		}
	}
}
