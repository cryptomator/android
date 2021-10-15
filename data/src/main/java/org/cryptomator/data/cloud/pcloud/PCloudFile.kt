package org.cryptomator.data.cloud.pcloud

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFile
import java.util.Date

internal class PCloudFile(override val parent: PCloudFolder, override val name: String, override val path: String, override val size: Long?, override val modified: Date?) : CloudFile, PCloudNode {

	override val cloud: Cloud?
		get() = parent.cloud
}
