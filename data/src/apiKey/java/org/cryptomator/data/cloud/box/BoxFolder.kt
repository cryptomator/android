package org.cryptomator.data.cloud.box

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFolder

open class BoxFolder(override val parent: BoxFolder?, override val name: String, override val path: String) : CloudFolder, BoxNode {

	override val isFolder: Boolean = true

	override val cloud: Cloud?
		get() = parent?.cloud

	override fun withCloud(cloud: Cloud?): BoxFolder? {
		return BoxFolder(parent?.withCloud(cloud), name, path)
	}
}
