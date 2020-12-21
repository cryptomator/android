package org.cryptomator.presentation.model

import org.cryptomator.data.cloud.crypto.CryptoCloud
import org.cryptomator.domain.Cloud

class CryptoCloudModel(cloud: Cloud) : CloudModel(cloud) {

	private val vault: VaultModel

	override fun name(): Int {
		throw IllegalStateException("Should not be invoked")
	}

	override fun username(): String? {
		return ""
	}

	override fun cloudType(): CloudTypeModel {
		return CloudTypeModel.CRYPTO
	}

	fun vault(): VaultModel {
		return vault
	}

	init {
		val cryptoCloud = cloud as CryptoCloud
		vault = VaultModel(cryptoCloud.vault)
	}
}
