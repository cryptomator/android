package org.cryptomator.data.cloud.googledrive

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.GoogleDriveCloud

class RootGoogleDriveFolder(override val cloud: GoogleDriveCloud) : GoogleDriveFolder(null, "", "", "root") {

	override fun withCloud(cloud: Cloud?): GoogleDriveFolder {
		return RootGoogleDriveFolder(cloud as GoogleDriveCloud)
	}
}
