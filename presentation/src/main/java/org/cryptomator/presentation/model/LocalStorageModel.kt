package org.cryptomator.presentation.model

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.LocalStorageCloud
import org.cryptomator.presentation.R
import org.cryptomator.util.Encodings
import java.net.URLDecoder

class LocalStorageModel(cloud: Cloud) : CloudModel(cloud) {

	override fun name(): Int {
		return R.string.cloud_names_local_storage
	}

	override fun username(): String? {
		return ""
	}

	override fun cloudType(): CloudTypeModel {
		return CloudTypeModel.LOCAL
	}

	private fun cloud(): LocalStorageCloud {
		return toCloud() as LocalStorageCloud
	}

	fun location(): String {
		val displayToken = prepareTokenForDisplay()
		return displayToken.substring(displayToken.lastIndexOf(":") + 1)
	}

	fun storage(): String {
		val displayToken = prepareTokenForDisplay()
		val displayTokenWithoutLocation = displayToken.replace(location(), "")
		return displayTokenWithoutLocation.substring(displayTokenWithoutLocation.lastIndexOf("/") + 1, displayTokenWithoutLocation.lastIndexOf(":"))
	}

	private fun prepareTokenForDisplay(): String {
		return URLDecoder.decode(cloud().rootUri(), Encodings.UTF_8.name())
	}

	fun uri(): String {
		return cloud().rootUri()
	}
}
