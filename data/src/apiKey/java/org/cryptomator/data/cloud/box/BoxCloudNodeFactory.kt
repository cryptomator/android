package org.cryptomator.data.cloud.box

import org.cryptomator.domain.exception.FatalBackendException
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.Date

internal object BoxCloudNodeFactory {

	fun from(parent: BoxFolder, item: BoxItem): BoxNode {
		return if (item.isFolder) {
			folder(parent, item)
		} else {
			file(parent, item)
		}
	}

	fun file(parent: BoxFolder, item: BoxItem): BoxFile {
		val name = requireName(item)
		return BoxFile(parent, name, getNodePath(parent, name), item.size, lastModified(item))
	}

	fun file(parent: BoxFolder, name: String, size: Long?): BoxFile {
		return BoxFile(parent, name, getNodePath(parent, name), size, null)
	}

	fun folder(parent: BoxFolder, item: BoxItem): BoxFolder {
		val name = requireName(item)
		return BoxFolder(parent, name, getNodePath(parent, name))
	}

	fun folder(parent: BoxFolder, name: String): BoxFolder {
		return BoxFolder(parent, name, getNodePath(parent, name))
	}

	private fun requireName(item: BoxItem): String {
		return item.name ?: throw FatalBackendException("Item name shouldn't be null")
	}

	private fun getNodePath(parent: BoxFolder, name: String): String {
		return parent.path + "/" + name
	}

	private fun lastModified(item: BoxItem): Date? {
		val timestamp = item.contentModifiedAt ?: item.modifiedAt ?: return null
		return try {
			Date.from(OffsetDateTime.parse(timestamp).toInstant())
		} catch (e: DateTimeParseException) {
			null
		}
	}
}
