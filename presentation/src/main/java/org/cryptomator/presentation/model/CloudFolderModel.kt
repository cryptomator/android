package org.cryptomator.presentation.model

import org.cryptomator.data.cloud.crypto.CryptoCloud
import org.cryptomator.domain.CloudFolder
import org.cryptomator.domain.CloudType
import org.cryptomator.domain.usecases.ResultRenamed

class CloudFolderModel(cloudFolder: CloudFolder) : CloudNodeModel<CloudFolder>(cloudFolder) {

	constructor(cloudFolderRenamed: ResultRenamed<CloudFolder>) : this(cloudFolderRenamed.value()) {
		oldName = cloudFolderRenamed.oldName
	}

	override val isFile: Boolean
		get() = false
	override val isFolder: Boolean
		get() = true

	fun vault(): VaultModel? {
		return if (toCloudNode().cloud?.type == CloudType.CRYPTO) {
			VaultModel((toCloudNode().cloud as CryptoCloud).vault)
		} else {
			null
		}
	}
}
