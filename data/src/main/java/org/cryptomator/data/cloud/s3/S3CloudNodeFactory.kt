package org.cryptomator.data.cloud.s3

import java.util.Date

internal object S3CloudNodeFactory {

	private const val DELIMITER = "/"

	fun file(parent: S3Folder, name: String): S3File {
		return S3File(parent, name, getNodePath(parent, name), null,  null)
	}

	fun file(parent: S3Folder, name: String, size: Long?): S3File {
		return S3File(parent, name, getNodePath(parent, name), size, null)
	}

	fun file(parent: S3Folder, name: String, size: Long?, path: String): S3File {
		return S3File(parent, name, path, size, null)
	}

	fun file(parent: S3Folder, name: String, size: Long?, lastModified: Date?): S3File {
		return S3File(parent, name, getNodePath(parent, name), size, lastModified)
	}

	fun folder(parent: S3Folder, name: String): S3Folder {
		return S3Folder(parent, name, getNodePath(parent, name))
	}

	fun folder(parent: S3Folder?, name: String, path: String): S3Folder {
		return S3Folder(parent, name, path)
	}

	private fun getNodePath(parent: S3Folder, name: String): String {
		return parent.path + "/" + name
	}

	fun getNameFromKey(key: String): String {
		var name = key
		if (key.endsWith(DELIMITER)) {
			name = key.substring(0, key.length - 1)
		}
		return if (name.contains(DELIMITER)) name.substring(name.lastIndexOf(DELIMITER) + 1) else name
	}
}
