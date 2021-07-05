package org.cryptomator.data.cloud.s3

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.S3Cloud

internal class RootS3Folder(override val cloud: S3Cloud) : S3Folder(null, "", "") {

	override val key: String
		get() = ""

	override fun withCloud(cloud: Cloud?): S3Folder {
		return RootS3Folder(cloud as S3Cloud)
	}
}
