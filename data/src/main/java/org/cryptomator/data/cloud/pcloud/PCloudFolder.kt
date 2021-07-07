package org.cryptomator.data.cloud.pcloud

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFolder

open class PCloudFolder(override val parent: PCloudFolder?, override val name: String, override val path: String) : CloudFolder, PCloudNode {

	override val cloud: Cloud?
		get() = parent?.cloud

	override fun withCloud(cloud: Cloud?): PCloudFolder? {
		return PCloudFolder(parent?.withCloud(cloud), name, path)
	}
}
