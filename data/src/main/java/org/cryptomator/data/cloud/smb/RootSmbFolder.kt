package org.cryptomator.data.cloud.smb

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.SmbCloud

class RootSmbFolder(override val cloud: SmbCloud) : SmbFolder(null, "", "") {

	override fun withCloud(cloud: Cloud?): SmbFolder {
		return RootSmbFolder(cloud as SmbCloud)
	}
}
