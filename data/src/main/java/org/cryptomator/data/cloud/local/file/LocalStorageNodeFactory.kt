package org.cryptomator.data.cloud.local.file

import java.io.File
import java.util.Date

internal object LocalStorageNodeFactory {

	@JvmStatic
	fun from(parent: LocalFolder, file: File): LocalNode {
		return if (file.isDirectory) {
			folder(parent, file)
		} else {
			file( //
				parent,  //
				file.name,  //
				file.path,  //
				file.length(),  //
				Date(file.lastModified())
			)
		}
	}

	fun folder(parent: LocalFolder, file: File): LocalFolder {
		return folder(parent, file.name, file.path)
	}

	@JvmStatic
	fun folder(parent: LocalFolder, name: String, path: String): LocalFolder {
		return LocalFolder(parent, name, path)
	}

	@JvmStatic
	fun file(folder: LocalFolder, name: String, path: String, size: Long?, modified: Date?): LocalFile {
		return LocalFile(folder, name, path, size, modified)
	}
}
