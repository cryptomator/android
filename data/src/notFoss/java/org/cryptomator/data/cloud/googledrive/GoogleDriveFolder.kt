package org.cryptomator.data.cloud.googledrive

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFolder

open class GoogleDriveFolder(override val parent: GoogleDriveFolder?, override val name: String, override val path: String, override val driveId: String?) : CloudFolder, GoogleDriveNode {

	override val cloud: Cloud?
		get() = parent?.cloud

	override fun withCloud(cloud: Cloud?): GoogleDriveFolder? {
		return GoogleDriveFolder(parent?.withCloud(cloud), name, path, driveId)
	}
}
