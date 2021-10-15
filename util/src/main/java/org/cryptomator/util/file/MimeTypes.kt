package org.cryptomator.util.file

import javax.inject.Inject

class MimeTypes @Inject constructor(private val mimeTypeMap: MimeTypeMap) {

	fun fromExtension(fileExtension: String): MimeType? {
		val mimeType = mimeTypeMap.getMimeTypeFromExtension(fileExtension.lowercase())
			?: return null
		val firstSlash = mimeType.indexOf('/')
		return if (firstSlash == -1) {
			null
		} else
			MimeType( //
				mimeType.substring(0, firstSlash),  //
				mimeType.substring(firstSlash + 1)
			)
	}

	fun fromFilename(filename: String): MimeType? {
		val lastDot = filename.lastIndexOf('.')
		return if (lastDot == -1) {
			null
		} else fromExtension(filename.substring(lastDot + 1))
	}
}
