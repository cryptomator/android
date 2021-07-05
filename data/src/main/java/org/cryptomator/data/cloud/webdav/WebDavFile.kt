package org.cryptomator.data.cloud.webdav

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFile
import java.util.Date

class WebDavFile(override val parent: WebDavFolder, override val name: String, override val path: String, override val size: Long?, override val modified: Date?) : CloudFile, WebDavNode {

	constructor(parent: WebDavFolder, name: String, size: Long?, modified: Date?) : this(parent, name, parent.path + "/" + name, size, modified)

	override val cloud: Cloud?
		get() = parent.cloud
}
