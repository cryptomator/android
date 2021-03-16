package org.cryptomator.presentation.model

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.PCloudCloud
import org.cryptomator.presentation.R

class PCloudCloudModel(cloud: Cloud) : CloudModel(cloud) {

	override fun name(): Int {
		return R.string.cloud_names_pcloud
	}

	override fun username(): String? {
		return cloud().username()
	}

	fun url(): String {
		return cloud().url()
	}

	fun id(): Long {
		return cloud().id()
	}

	private fun cloud(): PCloudCloud {
		return toCloud() as PCloudCloud
	}

	override fun cloudType(): CloudTypeModel {
		return CloudTypeModel.PCLOUD
	}
}
