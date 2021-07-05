package org.cryptomator.data.cloud.local.file

import org.cryptomator.domain.CloudNode

interface LocalNode : CloudNode {

	override val parent: LocalFolder?

}
