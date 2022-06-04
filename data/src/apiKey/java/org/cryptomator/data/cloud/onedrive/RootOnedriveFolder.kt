package org.cryptomator.data.cloud.onedrive

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.OnedriveCloud

internal class RootOnedriveFolder(override val cloud: OnedriveCloud) : OnedriveFolder(null, "", "") {

	override fun withCloud(cloud: Cloud?): RootOnedriveFolder {
		return RootOnedriveFolder(cloud as OnedriveCloud)
	}
}
