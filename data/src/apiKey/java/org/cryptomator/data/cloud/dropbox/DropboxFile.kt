package org.cryptomator.data.cloud.dropbox

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFile
import java.util.Date

internal class DropboxFile(override val parent: DropboxFolder, override val name: String, override val path: String, override val size: Long?, override val modified: Date?) : CloudFile, DropboxNode {

	override val cloud: Cloud?
		get() = parent.cloud
}
