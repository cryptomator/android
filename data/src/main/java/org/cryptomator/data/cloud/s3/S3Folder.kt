package org.cryptomator.data.cloud.s3

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFolder

internal open class S3Folder(override val parent: S3Folder?, override val name: String, override val path: String) : CloudFolder, S3Node {

	override val cloud: Cloud?
		get() = parent?.cloud

	override val key: String
		get() = if (path.startsWith(DELIMITER)) {
			path.substring(DELIMITER.length) + DELIMITER
		} else path + DELIMITER

	override fun withCloud(cloud: Cloud?): S3Folder? {
		return S3Folder(parent?.withCloud(cloud), name, path)
	}

	companion object {
		private const val DELIMITER = "/"
	}
}
