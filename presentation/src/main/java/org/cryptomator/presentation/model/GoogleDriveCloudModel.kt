package org.cryptomator.presentation.model

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.GoogleDriveCloud
import org.cryptomator.presentation.R

class GoogleDriveCloudModel(cloud: Cloud) : CloudModel(cloud) {

	override fun name(): Int {
		return R.string.cloud_names_google_drive
	}

	override fun username(): String? {
		return cloud().username()
	}

	override fun cloudType(): CloudTypeModel {
		return CloudTypeModel.GOOGLE_DRIVE
	}

	private fun cloud(): GoogleDriveCloud {
		return toCloud() as GoogleDriveCloud
	}
}
