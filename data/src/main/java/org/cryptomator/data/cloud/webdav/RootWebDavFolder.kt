package org.cryptomator.data.cloud.webdav

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.WebDavCloud

class RootWebDavFolder(override val cloud: WebDavCloud) : WebDavFolder(null, "", "") {

	override fun withCloud(cloud: Cloud?): WebDavFolder {
		return RootWebDavFolder(cloud as WebDavCloud)
	}
}
