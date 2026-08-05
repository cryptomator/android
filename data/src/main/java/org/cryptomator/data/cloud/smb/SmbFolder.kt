package org.cryptomator.data.cloud.smb

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFolder

/**
 * SMB Cloud folder implementation.
 * Skeleton for the first step of SMB support.
 */
open class SmbFolder(
	override val parent: SmbFolder?,
	override val name: String,
	override val cloud: Cloud
) : SmbNode, CloudFolder {

	override fun withCloud(cloud: Cloud?): CloudFolder? {
		return cloud?.let { SmbFolder(parent, name, it) }
	}
}
