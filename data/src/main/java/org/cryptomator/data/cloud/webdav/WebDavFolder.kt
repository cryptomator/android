package org.cryptomator.data.cloud.webdav;

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFolder

open class WebDavFolder(override val parent: WebDavFolder?, override val name: String, override val path: String) : CloudFolder, WebDavNode {

	override val cloud: Cloud?
		get() = parent?.cloud

	override fun withCloud(cloud: Cloud?): WebDavFolder? {
		return WebDavFolder(parent?.withCloud(cloud), name, path)
	}
}
