package org.cryptomator.data.cloud.microsoftgraph

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFile
import java.util.Date

internal class MicrosoftGraphFile(override val parent: MicrosoftGraphFolder, override val name: String, override val path: String, override val size: Long?, override val modified: Date?) : CloudFile, MicrosoftGraphNode {

	override val isFolder: Boolean = false

	override val cloud: Cloud?
		get() = parent.cloud
}
