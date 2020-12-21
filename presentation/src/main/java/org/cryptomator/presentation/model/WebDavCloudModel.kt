package org.cryptomator.presentation.model

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.WebDavCloud
import org.cryptomator.presentation.R

class WebDavCloudModel(cloud: Cloud) : CloudModel(cloud) {

	override fun name(): Int {
		return R.string.cloud_names_webdav
	}

	override fun username(): String? {
		return cloud().username()
	}

	override fun cloudType(): CloudTypeModel {
		return CloudTypeModel.WEBDAV
	}

	fun url(): String {
		return cloud().url()
	}

	fun accessToken(): String {
		return cloud().password()
	}

	fun id(): Long {
		return cloud().id()
	}

	fun certificate(): String? {
		return cloud().certificate()
	}

	private fun cloud(): WebDavCloud {
		return toCloud() as WebDavCloud
	}
}
