package org.cryptomator.util.file

import android.webkit.MimeTypeMap
import javax.inject.Inject

class MimeTypeMap @Inject constructor() {

	private val mimeTypeMap = MimeTypeMap.getSingleton()

	fun getMimeTypeFromExtension(extension: String?): String? {
		return mimeTypeMap.getMimeTypeFromExtension(extension)
	}

}
