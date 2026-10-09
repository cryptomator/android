package org.cryptomator.data.cloud.box

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFile
import java.util.Date

internal class BoxFile(override val parent: BoxFolder, override val name: String, override val path: String, override val size: Long?, override val modified: Date?) : CloudFile, BoxNode {

	override val isFolder: Boolean = false

	override val cloud: Cloud?
		get() = parent.cloud
}
