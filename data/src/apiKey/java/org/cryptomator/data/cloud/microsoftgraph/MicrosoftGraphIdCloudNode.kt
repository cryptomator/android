package org.cryptomator.data.cloud.microsoftgraph

import org.cryptomator.domain.CloudNode

interface MicrosoftGraphIdCloudNode : CloudNode {

	val id: String
	val driveId: String

}
