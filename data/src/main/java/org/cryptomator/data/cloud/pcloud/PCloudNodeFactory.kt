package org.cryptomator.data.cloud.pcloud

import com.pcloud.sdk.RemoteEntry
import com.pcloud.sdk.RemoteFile
import com.pcloud.sdk.RemoteFolder

internal object PCloudNodeFactory {

	fun file(parent: PCloudFolder, file: RemoteFile): PCloudFile {
		return PCloudFile(parent, file.name(), getNodePath(parent, file.name()), file.size(), file.lastModified())
	}

	fun file(parent: PCloudFolder, name: String, size: Long?): PCloudFile {
		return PCloudFile(parent, name, getNodePath(parent, name), size, null)
	}

	@JvmStatic
	fun file(parent: PCloudFolder, name: String, size: Long?, path: String): PCloudFile {
		return PCloudFile(parent, name, path, size, null)
	}

	fun folder(parent: PCloudFolder, folder: RemoteFolder): PCloudFolder {
		return PCloudFolder(parent, folder.name(), getNodePath(parent, folder.name()))
	}

	fun folder(parent: PCloudFolder, name: String): PCloudFolder {
		return PCloudFolder(parent, name, getNodePath(parent, name))
	}

	@JvmStatic
	fun folder(parent: PCloudFolder?, name: String, path: String): PCloudFolder {
		return PCloudFolder(parent, name, path)
	}

	fun getNodePath(parent: PCloudFolder, name: String): String {
		return parent.path + "/" + name
	}

	@JvmStatic
	fun from(parent: PCloudFolder, remoteEntry: RemoteEntry): PCloudNode {
		return if (remoteEntry is RemoteFile) {
			file(parent, remoteEntry.asFile())
		} else {
			folder(parent, remoteEntry.asFolder())
		}
	}
}
