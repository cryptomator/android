package org.cryptomator.presentation.model

import org.cryptomator.domain.BoxCloud
import org.cryptomator.domain.Cloud
import org.cryptomator.presentation.R

class BoxCloudModel(cloud: Cloud) : CloudModel(cloud) {

	override fun name(): Int {
		return R.string.cloud_names_box
	}

	override fun username(): String? {
		return cloud().username()
	}

	private fun cloud(): BoxCloud {
		return toCloud() as BoxCloud
	}

	override fun cloudType(): CloudTypeModel {
		return CloudTypeModel.BOX
	}
}
