package org.cryptomator.data.cloud.smb

import org.cryptomator.domain.CloudNode

interface SmbNode : CloudNode {

	override val parent: SmbFolder?

}
