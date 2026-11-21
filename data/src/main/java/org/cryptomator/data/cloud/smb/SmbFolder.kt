package org.cryptomator.data.cloud.smb

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFolder

open class SmbFolder(override val parent: SmbFolder?, override val name: String, override val path: String) : CloudFolder, SmbNode {

	override val cloud: Cloud?
		get() = parent?.cloud

	override fun withCloud(cloud: Cloud?): SmbFolder? {
		return SmbFolder(parent?.withCloud(cloud), name, path)
	}
}
