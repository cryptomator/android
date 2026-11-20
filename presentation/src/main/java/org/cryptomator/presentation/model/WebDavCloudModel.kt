package org.cryptomator.presentation.model

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.WebDavCloud
import org.cryptomator.domain.WebDavProvider

class WebDavCloudModel(cloud: Cloud) : CloudModel(cloud) {

	override fun name(): Int {
		return cloudType().displayNameResource
	}

	override fun username(): String? {
		return cloud().username()
	}

	override fun cloudType(): CloudTypeModel {
		return CloudTypeModel.valueOf(toCloud())
	}

	fun url(): String {
		return cloud().url()
	}

	fun accessToken(): String {
		return cloud().password()
	}

	fun id(): Long? {
		return cloud().id()
	}

	fun certificate(): String? {
		return cloud().certificate()
	}

	fun provider(): WebDavProvider? {
		return cloud().provider()
	}

	private fun cloud(): WebDavCloud {
		return toCloud() as WebDavCloud
	}
}
