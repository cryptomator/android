package org.cryptomator.data.cloud.box

import org.cryptomator.domain.BoxCloud
import org.cryptomator.domain.Cloud

internal class RootBoxFolder(override val cloud: BoxCloud) : BoxFolder(null, "", "") {

	override fun withCloud(cloud: Cloud?): RootBoxFolder {
		return RootBoxFolder(cloud as BoxCloud)
	}
}
