package org.cryptomator.presentation.model

import java.io.Serializable

data class AutoUploadFilesStore(
		val uris: Set<String>
) : Serializable {

	companion object {

		private const val serialVersionUID: Long = 8901228478188469059
	}
}
