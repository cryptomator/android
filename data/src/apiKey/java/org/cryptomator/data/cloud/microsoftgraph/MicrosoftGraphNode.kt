package org.cryptomator.data.cloud.microsoftgraph

import org.cryptomator.domain.CloudNode

interface MicrosoftGraphNode : CloudNode {

	val isFolder: Boolean
	override val name: String
	override val path: String
	override val parent: MicrosoftGraphFolder?

}
