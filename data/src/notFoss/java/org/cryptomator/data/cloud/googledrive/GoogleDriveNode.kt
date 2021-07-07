package org.cryptomator.data.cloud.googledrive

internal interface GoogleDriveNode : GoogleDriveIdCloudNode {

	override val parent: GoogleDriveFolder?

}
