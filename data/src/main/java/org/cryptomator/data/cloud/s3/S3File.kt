package org.cryptomator.data.cloud.s3

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFile
import java.util.Date

internal class S3File(override val parent: S3Folder, override val name: String, override val path: String, override val size: Long?, override val modified: Date?) : CloudFile, S3Node {

	override val cloud: Cloud?
		get() = parent.cloud

	override val key: String
		get() = if (path.startsWith(DELIMITER)) {
			path.substring(DELIMITER.length)
		} else path

	companion object {

		private const val DELIMITER = "/"

	}
}
