package org.cryptomator.data.cloud.smb

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFile
import java.util.Date

class SmbFile(
	override val parent: SmbFolder,
	override val name: String,
	override val cloud: Cloud,
	override val size: Long? = null,
	override val modified: Date? = null,
) : SmbNode, CloudFile {

	companion object {
		@Suppress("unused")
		private const val serialVersionUID = 2772551525048123287L
	}
}
