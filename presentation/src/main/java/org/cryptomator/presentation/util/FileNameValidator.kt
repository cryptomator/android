package org.cryptomator.presentation.util

class FileNameValidator {

	companion object {

		private val INVALID_CHARACTER_PATTERN = Regex("[\\\\/:*?\"<>|]")
		private val INVALID_FILENAME_PATTERN = Regex("${INVALID_CHARACTER_PATTERN.pattern}|\\.\$| \$")

		fun isInvalidName(name: String): Boolean {
			return INVALID_FILENAME_PATTERN.containsMatchIn(name)
		}

		fun sanitize(name: String): String {
			return name.replace(INVALID_CHARACTER_PATTERN, "_").trimEnd('.', ' ').ifEmpty { "_" }
		}
	}
}
