package org.cryptomator.data.cloud.crypto

import android.content.Context
import org.cryptomator.cryptolib.api.Cryptor
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.CloudFolder
import org.cryptomator.domain.CloudNode
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.exception.CloudNodeAlreadyExistsException
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.domain.repository.CloudContentRepository
import org.cryptomator.domain.usecases.ProgressAware
import org.cryptomator.domain.usecases.cloud.DataSource
import org.cryptomator.domain.usecases.cloud.DownloadState
import org.cryptomator.domain.usecases.cloud.FileTransferState
import org.cryptomator.domain.usecases.cloud.UploadState
import java.io.File
import java.io.OutputStream
import java.util.function.Supplier

internal class CryptoCloudContentRepository(context: Context, cloudContentRepository: CloudContentRepository<Cloud, CloudNode, CloudFolder, CloudFile>, cloud: CryptoCloud, cryptor: Supplier<Cryptor>) :
	CloudContentRepository<CryptoCloud, CryptoNode, CryptoFolder, CryptoFile> {

	private var cryptoImpl: CryptoImplDecorator

	@Synchronized
	@Throws(BackendException::class)
	override fun root(cloud: CryptoCloud): CryptoFolder {
		return cryptoImpl.root(cloud)
	}

	override fun resolve(cloud: CryptoCloud, path: String): CryptoFolder {
		return cryptoImpl.resolve(cloud, path)
	}

	@Throws(BackendException::class)
	override fun file(parent: CryptoFolder, name: String): CryptoFile {
		return cryptoImpl.file(parent, name)
	}

	@Throws(BackendException::class)
	override fun file(parent: CryptoFolder, name: String, size: Long?): CryptoFile {
		return cryptoImpl.file(parent, name, size)
	}

	@Throws(BackendException::class)
	override fun folder(parent: CryptoFolder, name: String): CryptoFolder {
		return cryptoImpl.folder(parent, name)
	}

	@Throws(BackendException::class)
	override fun exists(node: CryptoNode): Boolean {
		return cryptoImpl.exists(node)
	}

	@Throws(BackendException::class)
	override fun list(folder: CryptoFolder): List<CryptoNode> {
		return cryptoImpl.list(folder)
	}

	@Throws(BackendException::class)
	override fun create(folder: CryptoFolder): CryptoFolder {
		return try {
			cryptoImpl.create(folder)
		} catch (e: CloudNodeAlreadyExistsException) {
			throw CloudNodeAlreadyExistsException(folder.name)
		}
	}

	@Throws(BackendException::class)
	override fun move(source: CryptoFolder, target: CryptoFolder): CryptoFolder {
		return try {
			cryptoImpl.move(source, target)
		} catch (e: CloudNodeAlreadyExistsException) {
			throw CloudNodeAlreadyExistsException(target.name)
		}
	}

	@Throws(BackendException::class)
	override fun move(source: CryptoFile, target: CryptoFile): CryptoFile {
		return try {
			cryptoImpl.move(source, target)
		} catch (e: CloudNodeAlreadyExistsException) {
			throw CloudNodeAlreadyExistsException(target.name)
		}
	}

	@Throws(BackendException::class)
	override fun write(file: CryptoFile, data: DataSource, progressAware: ProgressAware<UploadState>, replace: Boolean, size: Long): CryptoFile {
		return cryptoImpl.write(file, data, progressAware, replace, size)
	}

	@Throws(BackendException::class)
	override fun read(file: CryptoFile, encryptedTmpFile: File?, data: OutputStream, progressAware: ProgressAware<DownloadState>) {
		cryptoImpl.read(file, data, progressAware)
	}

	@Throws(BackendException::class)
	override fun associateThumbnails(list: List<CryptoNode>, progressAware: ProgressAware<FileTransferState>) {
		cryptoImpl.associateThumbnails(list, progressAware)
	}

	@Throws(BackendException::class)
	override fun delete(node: CryptoNode) {
		cryptoImpl.delete(node)
	}

	@Throws(BackendException::class)
	override fun checkAuthenticationAndRetrieveCurrentAccount(cloud: CryptoCloud): String {
		return cryptoImpl.currentAccount(cloud)
	}

	@Throws(BackendException::class)
	override fun logout(cloud: CryptoCloud) {
		// empty
	}

	init {
		val vaultLocation: CloudFolder = try {
			cloudContentRepository.resolve(cloud.vault.cloud, cloud.vault.path)
		} catch (e: BackendException) {
			throw FatalBackendException(e)
		}

		cryptoImpl = when (cloud.vault.format) {
			8 -> CryptoImplVaultFormat8(context, cryptor, cloudContentRepository, vaultLocation, DirIdCacheFormat7(), cloud.vault.shorteningThreshold)
			7 -> CryptoImplVaultFormat7(context, cryptor, cloudContentRepository, vaultLocation, DirIdCacheFormat7())
			6, 5 -> CryptoImplVaultFormatPre7(context, cryptor, cloudContentRepository, vaultLocation, DirIdCacheFormatPre7())
			else -> throw IllegalStateException(String.format("No CryptoImpl for vault format %d.", cloud.vault.format))
		}
	}
}
