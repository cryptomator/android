package org.cryptomator.data.cloud.smb

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudNode

/**
 * SMB Cloud node interface.
 * Skeleton for the first step of SMB support.
 */
interface SmbNode : CloudNode {
	override val parent: SmbFolder?

	override val path: String
		get() {
			val parentPath = parent?.path ?: ""
			return if (parentPath.endsWith("/") || parentPath.isEmpty()) {
				parentPath + name
			} else {
				"$parentPath/$name"
			}
		}
}
