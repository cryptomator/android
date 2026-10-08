package org.cryptomator.data.cloud.microsoftgraph

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.MicrosoftGraphCloud

internal class RootMicrosoftGraphFolder(override val cloud: MicrosoftGraphCloud) : MicrosoftGraphFolder(null, "", "") {

	override fun withCloud(cloud: Cloud?): RootMicrosoftGraphFolder {
		return RootMicrosoftGraphFolder(cloud as MicrosoftGraphCloud)
	}
}
