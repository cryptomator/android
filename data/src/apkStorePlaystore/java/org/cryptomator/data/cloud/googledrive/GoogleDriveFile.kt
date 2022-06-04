package org.cryptomator.data.cloud.googledrive

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFile
import java.util.Date

internal class GoogleDriveFile(
	override val parent: GoogleDriveFolder,
	override val name: String,
	override val path: String,
	override val driveId: String?,
	override val size: Long?,
	override val modified: Date?
) : CloudFile, GoogleDriveNode {

	override val cloud: Cloud?
		get() = parent.cloud

}
