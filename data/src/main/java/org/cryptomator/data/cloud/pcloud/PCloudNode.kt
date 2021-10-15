package org.cryptomator.data.cloud.pcloud

import org.cryptomator.domain.CloudNode

interface PCloudNode : CloudNode {

	override val parent: PCloudFolder?
}
