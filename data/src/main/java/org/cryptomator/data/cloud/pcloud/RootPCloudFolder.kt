package org.cryptomator.data.cloud.pcloud

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.PCloud

internal class RootPCloudFolder(override val cloud: PCloud) : PCloudFolder(null, "", "") {

	override fun withCloud(cloud: Cloud?): PCloudFolder {
		return RootPCloudFolder(cloud as PCloud)
	}
}
