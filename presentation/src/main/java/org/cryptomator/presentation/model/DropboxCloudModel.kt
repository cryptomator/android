package org.cryptomator.presentation.model

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.DropboxCloud
import org.cryptomator.presentation.R

class DropboxCloudModel(cloud: Cloud) : CloudModel(cloud) {

	override fun name(): Int {
		return R.string.cloud_names_dropbox
	}

	override fun username(): String? {
		return cloud().username()
	}

	private fun cloud(): DropboxCloud {
		return toCloud() as DropboxCloud
	}

	override fun cloudType(): CloudTypeModel {
		return CloudTypeModel.DROPBOX
	}
}
