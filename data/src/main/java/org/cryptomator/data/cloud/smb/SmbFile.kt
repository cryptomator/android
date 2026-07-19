package org.cryptomator.data.cloud.smb

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFile
import java.util.Date

/**
 * SMB Cloud file implementation.
 * Skeleton for the first step of SMB support.
 */
class SmbFile(
	override val parent: SmbFolder,
	override val name: String,
	override val cloud: Cloud,
	override val size: Long? = null,
	override val modified: Date? = null
) : SmbNode, CloudFile
