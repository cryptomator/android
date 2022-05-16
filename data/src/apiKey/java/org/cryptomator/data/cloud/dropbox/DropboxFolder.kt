package org.cryptomator.data.cloud.dropbox

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFolder

open class DropboxFolder(override val parent: DropboxFolder?, override val name: String, override val path: String) : CloudFolder, DropboxNode {

	override val cloud: Cloud?
		get() = parent?.cloud

	override fun withCloud(cloud: Cloud?): DropboxFolder? {
		return DropboxFolder(parent?.withCloud(cloud), name, path)
	}
}
