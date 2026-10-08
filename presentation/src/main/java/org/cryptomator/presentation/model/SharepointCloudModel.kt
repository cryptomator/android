package org.cryptomator.presentation.model

import android.net.Uri
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.SharepointCloud
import org.cryptomator.presentation.R

class SharepointCloudModel(cloud: Cloud) : CloudModel(cloud) {

	override fun name(): Int {
		return R.string.cloud_names_sharepoint
	}

	override fun username(): String? {
		return cloud().username()
	}

	fun siteAndDriveName(): String {
		val siteUrl = Uri.parse(cloud().siteUrl())
		return String.format("%s%s • %s", siteUrl.authority, siteUrl.path.orEmpty(), cloud().driveName())
	}

	private fun cloud(): SharepointCloud {
		return toCloud() as SharepointCloud
	}

	override fun cloudType(): CloudTypeModel {
		return CloudTypeModel.SHAREPOINT
	}
}
