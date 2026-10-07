package org.cryptomator.data.cloud.onedrive

import org.cryptomator.domain.CloudNode

interface OnedriveNode : CloudNode {

	val isFolder: Boolean
	override val name: String
	override val path: String
	override val parent: OnedriveFolder?

}
