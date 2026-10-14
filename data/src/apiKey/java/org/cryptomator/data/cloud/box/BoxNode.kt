package org.cryptomator.data.cloud.box

import org.cryptomator.domain.CloudNode

interface BoxNode : CloudNode {

	val isFolder: Boolean
	override val name: String
	override val path: String
	override val parent: BoxFolder?

}
