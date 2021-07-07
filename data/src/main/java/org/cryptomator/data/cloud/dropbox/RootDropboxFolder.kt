package org.cryptomator.data.cloud.dropbox

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.DropboxCloud

internal class RootDropboxFolder(override val cloud: DropboxCloud) : DropboxFolder(null, "", "") {

	override fun withCloud(cloud: Cloud?): DropboxFolder {
		return RootDropboxFolder(cloud as DropboxCloud)
	}
}
