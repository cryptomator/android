package org.cryptomator.data.cloud.smb

/**
 * A share on an SMB server as configured by the user: `smb://host[:port]/share[/path]`.
 */
class SmbLocation private constructor(val host: String, val port: Int, val share: String, private val basePath: String) {

	/**
	 * @return the share-relative path (backslash separated, as expected by SMB) of the given cloud path
	 */
	fun pathOf(cloudPath: String): String {
		return "$basePath/$cloudPath" //
			.split('/') //
			.filter { it.isNotEmpty() } //
			.joinToString("\\")
	}

	companion object {

		const val SCHEME = "smb://"
		private const val DEFAULT_PORT = 445

		/**
		 * Brings user input into the `smb://host[:port]/share[/path]` form: accepts UNC paths (`\\host\share`),
		 * backslashes and a missing scheme and strips trailing separators.
		 */
		fun normalize(input: String): String {
			val trimmed = input.trim().replace('\\', '/').trimEnd('/')
			val withoutScheme = when {
				trimmed.startsWith(SCHEME, ignoreCase = true) -> trimmed.substring(SCHEME.length)
				trimmed.startsWith("//") -> trimmed.substring(2)
				else -> trimmed
			}
			return SCHEME + withoutScheme
		}

		/**
		 * @return the location described by a [normalized][normalize] url or `null` if it is invalid or doesn't name a share
		 */
		fun parse(url: String): SmbLocation? {
			if (!url.startsWith(SCHEME, ignoreCase = true)) {
				return null
			}
			val segments = url.substring(SCHEME.length).split('/')
			if (segments.size < 2 || segments.drop(1).any { it.isEmpty() }) {
				return null
			}
			val (host, port) = parseAuthority(segments[0]) ?: return null
			return SmbLocation(host, port, segments[1], segments.drop(2).joinToString("/"))
		}

		private fun parseAuthority(authority: String): Pair<String, Int>? {
			if (authority.contains('@')) {
				return null
			}
			val host: String
			val port: String?
			if (authority.startsWith('[')) {
				// IPv6 literal, e.g. [fe80::1]:445
				val end = authority.indexOf(']')
				if (end == -1) {
					return null
				}
				host = authority.substring(1, end)
				port = authority.substring(end + 1).takeIf { it.isNotEmpty() }?.removePrefix(":")
			} else if (authority.count { it == ':' } == 1) {
				host = authority.substringBefore(':')
				port = authority.substringAfter(':')
			} else {
				host = authority
				port = null
			}
			if (host.isEmpty() || host.any { it.isWhitespace() }) {
				return null
			}
			val portNumber = if (port == null) DEFAULT_PORT else port.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
			return Pair(host, portNumber)
		}
	}
}
