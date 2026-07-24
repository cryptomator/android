package org.cryptomator.data.cloud.smb

import org.cryptomator.domain.CloudNode

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
