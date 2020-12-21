package org.cryptomator.presentation.util

class FileNameValidator {

	companion object {
		private val INVALID_FILENAME_PATTERN = Regex("[\\\\/:*?\"<>|]|\\.\$| \$")

		fun isInvalidName(name: String): Boolean {
			return INVALID_FILENAME_PATTERN.containsMatchIn(name)
		}
	}
}
