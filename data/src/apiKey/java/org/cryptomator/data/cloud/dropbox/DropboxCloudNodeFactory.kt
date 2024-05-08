package org.cryptomator.data.cloud.dropbox

import com.dropbox.core.v2.files.FileMetadata
import com.dropbox.core.v2.files.FolderMetadata
import com.dropbox.core.v2.files.Metadata

internal object DropboxCloudNodeFactory {

	fun from(parent: DropboxFolder, metadata: FileMetadata): DropboxFile {
		return DropboxFile(parent, metadata.name, metadata.pathDisplay, metadata.size, metadata.clientModified)
	}

	@JvmStatic
	fun file(parent: DropboxFolder, name: String, size: Long?, path: String): DropboxFile {
		return DropboxFile(parent, name, path, size, null)
	}

	fun from(parent: DropboxFolder, metadata: FolderMetadata): DropboxFolder {
		return DropboxFolder(parent, metadata.name, getNodePath(parent, metadata.name))
	}

	private fun getNodePath(parent: DropboxFolder, name: String): String {
		return parent.path + "/" + name
	}

	@JvmStatic
	fun folder(parent: DropboxFolder?, name: String, path: String): DropboxFolder {
		return DropboxFolder(parent, name, path)
	}

	@JvmStatic
	fun from(parent: DropboxFolder, metadata: Metadata): DropboxNode {
		return if (metadata is FileMetadata) {
			from(parent, metadata)
		} else {
			from(parent, metadata as FolderMetadata)
		}
	}
}
