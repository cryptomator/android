package org.cryptomator.presentation.model

import org.cryptomator.domain.CloudFile
import org.cryptomator.presentation.util.FileIcon

class FileProgressStateModel(file: CloudFile, icon: FileIcon, name: String, image: Image, text: Text) : ProgressStateModel(name, image, text) {

	val file: CloudFileModel = CloudFileModel(file, icon)

	fun `is`(name: String): Boolean {
		return name == name()
	}

	companion object {
		const val UPLOAD = "UPLOAD"
		const val ENCRYPTION = "ENCRYPTION"
		const val DOWNLOAD = "DOWNLOAD"
		const val DECRYPTION = "DECRYPTION"
	}

}
