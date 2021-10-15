package org.cryptomator.data.cloud.crypto

import org.cryptomator.domain.CloudFolder

interface DirIdCache {

	operator fun get(folder: CryptoFolder): DirIdInfo?

	fun put(folder: CryptoFolder, dirIdInfo: DirIdInfo): DirIdInfo

	fun evict(folder: CryptoFolder)

	fun evictSubFoldersOf(cryptoFolder: CryptoFolder)

	class DirIdInfo internal constructor(val id: String, val cloudFolder: CloudFolder) {

		fun withCloudFolder(cloudFolder: CloudFolder): DirIdInfo {
			return DirIdInfo(id, cloudFolder)
		}
	}
}
