package org.cryptomator.data.cloud.googledrive

import org.cryptomator.domain.CloudNode

internal interface GoogleDriveIdCloudNode : CloudNode {

	val driveId: String?

}
