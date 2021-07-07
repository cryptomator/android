package org.cryptomator.data.cloud.local.file

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFile
import java.util.Date

class LocalFile(override val parent: LocalFolder, override val name: String, override val path: String, override val size: Long?, override val modified: Date?) : CloudFile, LocalNode {

	override val cloud: Cloud?
		get() = parent.cloud
}
