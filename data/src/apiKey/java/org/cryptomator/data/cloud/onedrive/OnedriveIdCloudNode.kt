package org.cryptomator.data.cloud.onedrive

import org.cryptomator.domain.CloudNode

interface OnedriveIdCloudNode : CloudNode {

	val id: String
	val driveId: String

}
