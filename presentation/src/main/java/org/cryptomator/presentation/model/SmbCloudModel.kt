package org.cryptomator.presentation.model

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.SmbCloud
import org.cryptomator.presentation.R

/**
 * SMB Cloud model.
 * Currently just a skeleton for the first step of SMB support.
 */
class SmbCloudModel(cloud: Cloud) : CloudModel(cloud) {

	override fun name(): Int {
		return R.string.cloud_names_smb
	}

	override fun username(): String? {
		return smbCloud().username()
	}

	fun domain(): String? {
		return smbCloud().domain()
	}

	override fun cloudType(): CloudTypeModel {
		return CloudTypeModel.SMB
	}

	fun url(): String? {
		return smbCloud().url()
	}

	fun password(): String? {
		return smbCloud().password()
	}

	fun id(): Long? {
		return smbCloud().id()
	}

	private fun smbCloud(): SmbCloud {
		return toCloud() as SmbCloud
	}
}
