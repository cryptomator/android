package org.cryptomator.presentation.model

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.SmbCloud
import org.cryptomator.presentation.R

class SmbCloudModel(cloud: Cloud) : CloudModel(cloud) {

	override fun name(): Int {
		return R.string.cloud_names_smb
	}

	override fun username(): String? {
		return cloud().username()
	}

	override fun cloudType(): CloudTypeModel {
		return CloudTypeModel.SMB
	}

	fun url(): String {
		return cloud().url()
	}

	fun password(): String {
		return cloud().password()
	}

	fun id(): Long? {
		return cloud().id()
	}

	private fun cloud(): SmbCloud {
		return toCloud() as SmbCloud
	}
}
