package org.cryptomator.data.cloud.smb

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFile
import java.util.Date

class SmbFile(override val parent: SmbFolder, override val name: String, override val path: String, override val size: Long?, override val modified: Date?) : CloudFile, SmbNode {

	override val cloud: Cloud?
		get() = parent.cloud
}
