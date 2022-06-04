package org.cryptomator.data.cloud.onedrive

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFolder

open class OnedriveFolder(override val parent: OnedriveFolder?, override val name: String, override val path: String) : CloudFolder, OnedriveNode {

	override val isFolder: Boolean = true

	override val cloud: Cloud?
		get() = parent?.cloud

	override fun withCloud(cloud: Cloud?): OnedriveFolder? {
		return OnedriveFolder(parent?.withCloud(cloud), name, path)
	}
}
