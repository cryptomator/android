package org.cryptomator.data.repository

import org.cryptomator.data.cloud.CloudContentRepositoryFactories
import org.cryptomator.data.cloud.crypto.CryptoCloud
import org.cryptomator.data.cloud.crypto.CryptoCloudContentRepositoryFactory
import org.cryptomator.data.util.NetworkConnectionCheck
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.CloudFolder
import org.cryptomator.domain.CloudNode
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.exception.authentication.AuthenticationException
import org.cryptomator.domain.repository.CloudContentRepository
import org.cryptomator.domain.usecases.ProgressAware
import org.cryptomator.domain.usecases.cloud.DataSource
import org.cryptomator.domain.usecases.cloud.DownloadState
import org.cryptomator.domain.usecases.cloud.FileTransferState
import org.cryptomator.domain.usecases.cloud.UploadState
import java.io.File
import java.io.OutputStream
import java.util.WeakHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DispatchingCloudContentRepository @Inject constructor(
	private val cloudContentRepositoryFactories: CloudContentRepositoryFactories,
	private val networkConnectionCheck: NetworkConnectionCheck,
	private val cryptoCloudContentRepositoryFactory: CryptoCloudContentRepositoryFactory
) : CloudContentRepository<Cloud, CloudNode, CloudFolder, CloudFile> {

	private val delegates: MutableMap<Cloud, CloudContentRepository<Cloud, CloudNode, CloudFolder, CloudFile>> = WeakHashMap()

	@Throws(BackendException::class)
	override fun root(cloud: Cloud): CloudFolder {
		return try {
			networkConnectionCheck.assertConnectionIsPresent(cloud)
			delegateFor(cloud).root(cloud)
		} catch (e: AuthenticationException) {
			delegates.remove(cloud)
			throw e
		}
	}

	@Throws(BackendException::class)
	override fun resolve(cloud: Cloud, path: String): CloudFolder {
		return try {
			// do not check for network connection
			delegateFor(cloud).resolve(cloud, path)
		} catch (e: AuthenticationException) {
			delegates.remove(cloud)
			throw e
		}
	}

	@Throws(BackendException::class)
	override fun file(parent: CloudFolder, name: String): CloudFile {
		return try {
			parent.cloud?.let { networkConnectionCheck.assertConnectionIsPresent(it) } ?: throw IllegalStateException("Parent's cloud shouldn't be null")
			delegateFor(parent).file(parent, name)
		} catch (e: AuthenticationException) {
			delegates.remove(parent.cloud)
			throw e
		}
	}

	@Throws(BackendException::class)
	override fun file(parent: CloudFolder, name: String, size: Long?): CloudFile {
		return try {
			parent.cloud?.let { networkConnectionCheck.assertConnectionIsPresent(it) } ?: throw IllegalStateException("Parent's cloud shouldn't be null")
			delegateFor(parent).file(parent, name, size)
		} catch (e: AuthenticationException) {
			delegates.remove(parent.cloud)
			throw e
		}
	}

	@Throws(BackendException::class)
	override fun folder(parent: CloudFolder, name: String): CloudFolder {
		return try {
			parent.cloud?.let { networkConnectionCheck.assertConnectionIsPresent(it) } ?: throw IllegalStateException("Parent's cloud shouldn't be null")
			delegateFor(parent).folder(parent, name)
		} catch (e: AuthenticationException) {
			delegates.remove(parent.cloud)
			throw e
		}
	}

	@Throws(BackendException::class)
	override fun exists(node: CloudNode): Boolean {
		return try {
			node.cloud?.let { networkConnectionCheck.assertConnectionIsPresent(it) } ?: throw IllegalStateException("Parent's cloud shouldn't be null")
			delegateFor(node).exists(node)
		} catch (e: AuthenticationException) {
			delegates.remove(node.cloud)
			throw e
		}
	}

	@Throws(BackendException::class)
	override fun list(folder: CloudFolder): List<CloudNode> {
		return try {
			folder.cloud?.let { networkConnectionCheck.assertConnectionIsPresent(it) } ?: throw IllegalStateException("Parent's cloud shouldn't be null")
			delegateFor(folder).list(folder)
		} catch (e: AuthenticationException) {
			delegates.remove(folder.cloud)
			throw e
		}
	}

	@Throws(BackendException::class)
	override fun create(folder: CloudFolder): CloudFolder {
		return try {
			folder.cloud?.let { networkConnectionCheck.assertConnectionIsPresent(it) } ?: throw IllegalStateException("Parent's cloud shouldn't be null")
			delegateFor(folder).create(folder)
		} catch (e: AuthenticationException) {
			delegates.remove(folder.cloud)
			throw e
		}
	}

	@Throws(BackendException::class)
	override fun move(source: CloudFolder, target: CloudFolder): CloudFolder {
		return try {
			source.cloud?.let { networkConnectionCheck.assertConnectionIsPresent(it) } ?: throw IllegalStateException("Parent's cloud shouldn't be null")
			require(source.cloud == target.cloud) { "Cloud of parameters must match" }
			delegateFor(source).move(source, target)
		} catch (e: AuthenticationException) {
			delegates.remove(source.cloud)
			throw e
		}
	}

	@Throws(BackendException::class)
	override fun move(source: CloudFile, target: CloudFile): CloudFile {
		return try {
			source.cloud?.let { networkConnectionCheck.assertConnectionIsPresent(it) } ?: throw IllegalStateException("Parent's cloud shouldn't be null")
			require(source.cloud == target.cloud) { "Cloud of parameters must match" }
			delegateFor(source).move(source, target)
		} catch (e: AuthenticationException) {
			delegates.remove(source.cloud)
			throw e
		}
	}

	@Throws(BackendException::class)
	override fun write(file: CloudFile, data: DataSource, progressAware: ProgressAware<UploadState>, replace: Boolean, size: Long): CloudFile {
		return try {
			file.cloud?.let { networkConnectionCheck.assertConnectionIsPresent(it) } ?: throw IllegalStateException("Parent's cloud shouldn't be null")
			delegateFor(file).write(file, data, progressAware, replace, size)
		} catch (e: AuthenticationException) {
			delegates.remove(file.cloud)
			throw e
		}
	}

	@Throws(BackendException::class)
	override fun read(file: CloudFile, encryptedTmpFile: File?, data: OutputStream, progressAware: ProgressAware<DownloadState>) {
		try {
			file.cloud?.let { networkConnectionCheck.assertConnectionIsPresent(it) } ?: throw IllegalStateException("Parent's cloud shouldn't be null")
			delegateFor(file).read(file, encryptedTmpFile, data, progressAware)
		} catch (e: AuthenticationException) {
			delegates.remove(file.cloud)
			throw e
		}
	}

	@Throws(BackendException::class)
	override fun associateThumbnails(list: List<CloudNode>, progressAware: ProgressAware<FileTransferState>) {
		if (list.isEmpty()) {
			return
		}
		try {
			list[0].cloud?.let { networkConnectionCheck.assertConnectionIsPresent(it) } ?: throw IllegalStateException("Parent's cloud shouldn't be null")
			delegateFor(list[0]).associateThumbnails(list, progressAware)
		} catch (e: AuthenticationException) {
			delegates.remove(list[0].cloud)
			throw e
		}
	}

	@Throws(BackendException::class)
	override fun delete(node: CloudNode) {
		try {
			node.cloud?.let { networkConnectionCheck.assertConnectionIsPresent(it) } ?: throw IllegalStateException("Parent's cloud shouldn't be null")
			delegateFor(node).delete(node)
		} catch (e: AuthenticationException) {
			delegates.remove(node.cloud)
			throw e
		}
	}

	@Throws(BackendException::class)
	override fun checkAuthenticationAndRetrieveCurrentAccount(cloud: Cloud): String {
		return try {
			networkConnectionCheck.assertConnectionIsPresent(cloud)
			delegateFor(cloud).checkAuthenticationAndRetrieveCurrentAccount(cloud)
		} catch (e: AuthenticationException) {
			delegates.remove(cloud)
			throw e
		}
	}

	@Throws(BackendException::class)
	override fun logout(cloud: Cloud) {
		delegateFor(cloud).logout(cloud)
		removeCloudContentRepositoryFor(cloud)
	}

	fun removeCloudContentRepositoryFor(cloud: Cloud) {
		val clouds = delegates.keys.iterator()
		while (clouds.hasNext()) {
			val current = clouds.next()
			if (cloud == current) {
				clouds.remove()
			} else if (cloudIsDelegateOfCryptoCloud(current, cloud)) {
				cryptoCloudContentRepositoryFactory.deregisterCryptor((current as CryptoCloud).vault, false)
			}
		}
	}

	fun updateCloudContentRepositoryFor(cloud: Cloud) {
		val clouds = delegates.keys.iterator()
		while (clouds.hasNext()) {
			val current = clouds.next()
			if (cloudIsDelegateOfCryptoCloud(current, cloud)) {
				cryptoCloudContentRepositoryFactory.updateCloudInCryptor((current as CryptoCloud).vault, cloud)
			}
		}
	}

	private fun cloudIsDelegateOfCryptoCloud(potentialCryptoCloud: Cloud, cloud: Cloud): Boolean {
		if (potentialCryptoCloud is CryptoCloud) {
			val delegate = potentialCryptoCloud.vault.cloud
			return cloud == delegate
		}
		return false
	}

	private fun delegateFor(cloudNode: CloudNode): CloudContentRepository<Cloud, CloudNode, CloudFolder, CloudFile> {
		return cloudNode.cloud?.let {
			delegateFor(it)
		} ?: throw IllegalStateException("CloudNode's cloud shouldn't be null")
	}

	private fun delegateFor(cloud: Cloud): CloudContentRepository<Cloud, CloudNode, CloudFolder, CloudFile> {
		return delegates.getOrPut(cloud) {
			createCloudContentRepositoryFor(cloud)
		}
	}

	private fun createCloudContentRepositoryFor(cloud: Cloud): CloudContentRepository<Cloud, CloudNode, CloudFolder, CloudFile> {
		for (cloudContentRepositoryFactory in cloudContentRepositoryFactories) {
			if (cloudContentRepositoryFactory.supports(cloud)) {
				return cloudContentRepositoryFactory.cloudContentRepositoryFor(cloud)
			}
		}
		throw IllegalStateException("Unsupported cloud $cloud")
	}
}
