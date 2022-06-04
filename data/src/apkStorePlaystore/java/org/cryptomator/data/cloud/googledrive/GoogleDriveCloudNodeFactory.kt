package org.cryptomator.data.cloud.googledrive

import com.google.api.services.drive.model.File
import java.util.Date

internal object GoogleDriveCloudNodeFactory {

	fun file(parent: GoogleDriveFolder, file: File): GoogleDriveFile {
		return GoogleDriveFile(parent, file.name, getNodePath(parent, file.name), file.id, getFileSize(file), getModified(file))
	}

	fun file(parent: GoogleDriveFolder, name: String, size: Long?): GoogleDriveFile {
		return GoogleDriveFile(parent, name, getNodePath(parent, name), null, size, null)
	}

	private fun getModified(file: File): Date? {
		return if (file.modifiedTime != null) Date(file.modifiedTime.value) else null
	}

	private fun getFileSize(file: File): Long? {
		return if (file.getSize() != null) file.getSize() else null
	}

	fun file(parent: GoogleDriveFolder, name: String, size: Long?, path: String, driveId: String): GoogleDriveFile {
		return GoogleDriveFile(parent, name, path, driveId, size, null)
	}

	fun folder(parent: GoogleDriveFolder, file: File): GoogleDriveFolder {
		return GoogleDriveFolder(parent, file.name, getNodePath(parent, file.name), file.id)
	}

	fun folder(parent: GoogleDriveFolder, name: String): GoogleDriveFolder {
		return GoogleDriveFolder(parent, name, getNodePath(parent, name), null)
	}

	fun folder(parent: GoogleDriveFolder?, name: String, path: String, driveId: String): GoogleDriveFolder {
		return GoogleDriveFolder(parent, name, path, driveId)
	}

	fun from(parent: GoogleDriveFolder, file: File): GoogleDriveNode {
		return when {
			isFolder(file) -> {
				folder(parent, file)
			}
			isShortcutFolder(file) -> {
				folder(parent, file.name, getNodePath(parent, file.name), file.shortcutDetails.targetId)
			}
			else -> {
				file(parent, file)
			}
		}
	}

	fun isFolder(file: File): Boolean {
		return file.mimeType == "application/vnd.google-apps.folder"
	}

	fun isShortcutFolder(file: File): Boolean {
		return file.mimeType == "application/vnd.google-apps.shortcut" && file.shortcutDetails.targetMimeType == "application/vnd.google-apps.folder"
	}

	fun getNodePath(parent: GoogleDriveFolder, name: String): String {
		return parent.path + "/" + name
	}
}
