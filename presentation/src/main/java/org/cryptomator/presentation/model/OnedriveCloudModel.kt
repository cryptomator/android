package org.cryptomator.presentation.model

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.OnedriveCloud
import org.cryptomator.presentation.R

class OnedriveCloudModel(cloud: Cloud) : CloudModel(cloud) {

	override fun name(): Int {
		return R.string.cloud_names_onedrive
	}

	override fun username(): String? {
		return cloud().username()
	}

	private fun cloud(): OnedriveCloud {
		return toCloud() as OnedriveCloud
	}

	override fun cloudType(): CloudTypeModel {
		return CloudTypeModel.ONEDRIVE
	}
}
