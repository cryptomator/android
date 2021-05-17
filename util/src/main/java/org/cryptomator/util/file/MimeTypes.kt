package org.cryptomator.util.file

import org.cryptomator.util.Optional
import javax.inject.Inject

class MimeTypes @Inject constructor(private val mimeTypeMap: MimeTypeMap) {

	fun fromExtension(fileExtension: String): Optional<MimeType> {
		val mimeType = mimeTypeMap.getMimeTypeFromExtension(fileExtension.lowercase())
			?: return Optional.empty()
		val firstSlash = mimeType.indexOf('/')
		return if (firstSlash == -1) {
			Optional.empty()
		} else Optional.of(
			MimeType( //
				mimeType.substring(0, firstSlash),  //
				mimeType.substring(firstSlash + 1)
			)
		)
	}

	fun fromFilename(filename: String): Optional<MimeType> {
		val lastDot = filename.lastIndexOf('.')
		return if (lastDot == -1) {
			Optional.empty()
		} else fromExtension(filename.substring(lastDot + 1))
	}
}
