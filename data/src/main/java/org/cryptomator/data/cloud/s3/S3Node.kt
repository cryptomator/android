package org.cryptomator.data.cloud.s3

import org.cryptomator.domain.CloudNode

internal interface S3Node : CloudNode {

	override val parent: S3Folder?
	val key: String?

}
