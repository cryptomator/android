package org.cryptomator.data.cloud.okhttplogging

import java.util.HashSet

internal class HeaderNames(vararg headerNames: String) {

	private val lowercaseNames: MutableSet<String> = HashSet()

	operator fun contains(headerName: String): Boolean {
		return lowercaseNames.contains(headerName.lowercase())
	}

	init {
		headerNames.mapTo(lowercaseNames) { it.lowercase() }
	}
}
