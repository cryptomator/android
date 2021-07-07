package org.cryptomator.data.cloud.crypto

import org.cryptomator.domain.Cloud

class RootCryptoFolder(override val cloud: CryptoCloud) : CryptoFolder(null, "", "", null) {

	override fun withCloud(cloud: Cloud?): CryptoFolder {
		return RootCryptoFolder(cloud as CryptoCloud)
	}

	companion object {

		@JvmStatic
		fun isRoot(folder: CryptoFolder): Boolean {
			return folder is RootCryptoFolder
		}
	}
}
