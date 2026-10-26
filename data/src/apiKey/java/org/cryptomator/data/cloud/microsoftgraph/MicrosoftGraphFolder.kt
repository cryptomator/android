package org.cryptomator.data.cloud.microsoftgraph

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFolder

open class MicrosoftGraphFolder(override val parent: MicrosoftGraphFolder?, override val name: String, override val path: String) : CloudFolder, MicrosoftGraphNode {

	override val isFolder: Boolean = true

	override val cloud: Cloud?
		get() = parent?.cloud

	override fun withCloud(cloud: Cloud?): MicrosoftGraphFolder? {
		return MicrosoftGraphFolder(parent?.withCloud(cloud), name, path)
	}
}
