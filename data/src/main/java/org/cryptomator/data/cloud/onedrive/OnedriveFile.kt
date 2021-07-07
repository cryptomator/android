package org.cryptomator.data.cloud.onedrive

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFile
import java.util.Date

internal class OnedriveFile(override val parent: OnedriveFolder, override val name: String, override val path: String, override val size: Long?, override val modified: Date?) : CloudFile, OnedriveNode {

	override val isFolder: Boolean = false

	override val cloud: Cloud?
		get() = parent.cloud
}
