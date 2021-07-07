package org.cryptomator.data.cloud.local.file

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFolder

open class LocalFolder(override val parent: LocalFolder?, override val name: String, override val path: String) : CloudFolder, LocalNode {

	override val cloud: Cloud?
		get() = parent?.cloud

	override fun withCloud(cloud: Cloud?): LocalFolder? {
		return LocalFolder(parent?.withCloud(cloud), name, path)
	}
}
