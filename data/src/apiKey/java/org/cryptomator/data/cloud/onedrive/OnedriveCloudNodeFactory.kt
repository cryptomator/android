package org.cryptomator.data.cloud.onedrive

import com.microsoft.graph.models.DriveItem
import org.cryptomator.domain.exception.FatalBackendException
import java.util.Date

internal object OnedriveCloudNodeFactory {

	@JvmStatic
	fun from(parent: OnedriveFolder, item: DriveItem): OnedriveNode {
		return if (isFolder(item)) {
			folder(parent, item)
		} else {
			file(parent, item)
		}
	}

	private fun file(parent: OnedriveFolder, item: DriveItem): OnedriveFile {
		item.name?.let {
			return OnedriveFile(parent, it, getNodePath(parent, it), item.size, lastModified(item))
		} ?: throw FatalBackendException("Item name shouldn't be null")
	}

	fun file(parent: OnedriveFolder, item: DriveItem, lastModified: Date?): OnedriveFile {
		item.name?.let {
			return OnedriveFile(parent, it, getNodePath(parent, it), item.size, lastModified)
		} ?: throw FatalBackendException("Item name shouldn't be null")
	}

	fun file(parent: OnedriveFolder, name: String, size: Long?): OnedriveFile {
		return OnedriveFile(parent, name, getNodePath(parent, name), size, null)
	}

	fun file(parent: OnedriveFolder, name: String, size: Long?, path: String): OnedriveFile {
		return OnedriveFile(parent, name, path, size, null)
	}

	fun folder(parent: OnedriveFolder, item: DriveItem): OnedriveFolder {
		item.name?.let {
			return OnedriveFolder(parent, it, getNodePath(parent, it))
		} ?: throw FatalBackendException("Item name shouldn't be null")
	}

	fun folder(parent: OnedriveFolder, name: String): OnedriveFolder {
		return OnedriveFolder(parent, name, getNodePath(parent, name))
	}

	fun folder(parent: OnedriveFolder, name: String, path: String): OnedriveFolder {
		return OnedriveFolder(parent, name, path)
	}

	private fun getNodePath(parent: OnedriveFolder, name: String): String {
		return parent.path + "/" + name
	}

	@JvmStatic
	fun getId(item: DriveItem): String {
		return if (item.remoteItem != null) item.remoteItem?.id!!
		else item.id!!
	}

	@JvmStatic
	fun getDriveId(item: DriveItem): String? {
		return when {
			item.remoteItem != null -> item.remoteItem?.parentReference?.driveId
			item.parentReference != null -> item.parentReference?.driveId
			else -> null
		}
	}

	@JvmStatic
	fun isFolder(item: DriveItem): Boolean {
		return item.folder != null || item.remoteItem != null && item.remoteItem?.folder != null
	}

	private fun lastModified(item: DriveItem): Date? {
		return item.fileSystemInfo?.lastModifiedDateTime?.let { clientDate -> Date.from(clientDate.toInstant()) }
			?: item.lastModifiedDateTime?.let { serverDate -> Date.from(serverDate.toInstant()) }
	}
}
