package org.cryptomator.data.cloud.webdav.network

import org.cryptomator.data.cloud.webdav.WebDavFile
import org.cryptomator.data.cloud.webdav.WebDavFolder
import org.cryptomator.data.cloud.webdav.WebDavNode
import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.util.Date
import java.util.regex.Pattern

internal class PropfindEntryData {

	var path: String? = null
		private set

	lateinit var pathSegments: Array<String>

	private var isFile = true

	private var lastModified: Date? = null

	var size: Long? = null

	private fun extractPath(pathOrUri: String): String {
		val matcher = URI_PATTERN.matcher(pathOrUri)
		return if (matcher.matches()) {
			urlDecode(matcher.group(1))
		} else if (!pathOrUri.startsWith("/")) {
			urlDecode("/$pathOrUri")
		} else {
			urlDecode(pathOrUri)
		}
	}

	fun setLastModified(lastModified: Date?) {
		this.lastModified = lastModified
	}

	fun setPath(pathOrUri: String) {
		path = extractPath(pathOrUri).also {
			var pathSegs = it.split("/")
			if (pathSegs.last() == "") {
				pathSegs = pathSegs.subList(0, pathSegs.size - 1)
			}
			pathSegments = pathSegs.toTypedArray()
		}
	}

	fun toCloudNode(parent: WebDavFolder): WebDavNode {
		return if (isFile) {
			WebDavFile(parent, getName(), size, lastModified)
		} else {
			WebDavFolder(parent, getName(), parent.path + '/' + getName())
		}
	}

	private fun urlDecode(value: String): String {
		return try {
			URLDecoder.decode(value, "UTF-8")
		} catch (e: UnsupportedEncodingException) {
			throw IllegalStateException("UTF-8 must be supported by every JVM", e)
		}
	}

	fun getDepth(): Int {
		return pathSegments.size
	}

	private fun getName(): String {
		return pathSegments[pathSegments.size - 1]
	}

	fun setFile(boolean: Boolean) {
		isFile = boolean
	}

	companion object {

		private val URI_PATTERN = Pattern.compile("^[a-z]+://[^/]+/(.*)$")
	}
}
