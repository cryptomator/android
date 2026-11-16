package org.cryptomator.presentation.util

import java.net.URI
import java.net.URISyntaxException

class SharepointUrlParser {

	companion object {

		private val SHAREPOINT_HOST = Regex("^[a-z0-9-]+\\.sharepoint\\.com$")
		private val SHARING_LINK_TYPE = Regex("^:[a-z]+:$")
		private val SITE_COLLECTION_PATHS = setOf("sites", "teams")

		/**
		 * Reduces a link into a SharePoint site, such as a page, a library, a file or an `r`/`s`/`t` sharing link, to the
		 * URL of its `/sites/` or `/teams/` site collection. Links outside a site collection fall back to the tenant's root site.
		 * Returns null for malformed or non-https input and for hosts other than `*.sharepoint.com`. It also returns null for
		 * `-my` hosts because they are OneDrive for Business, and for other sharing links because they don't name their site.
		 */
		fun siteUrlOf(url: String): String? {
			val uri = try {
				URI(url.trim())
			} catch (e: URISyntaxException) {
				return null
			}
			val host = uri.host?.lowercase() ?: return null
			if (uri.scheme != "https" || !SHAREPOINT_HOST.matches(host) || host.endsWith("-my.sharepoint.com")) {
				return null
			}
			val segments = pathSegmentsOf(uri.rawPath.orEmpty()) ?: return null
			val siteCollectionPath = segments.firstOrNull()?.lowercase()
			return if (segments.size >= 2 && siteCollectionPath in SITE_COLLECTION_PATHS) {
				"https://$host/$siteCollectionPath/${segments[1]}"
			} else {
				"https://$host"
			}
		}

		private fun pathSegmentsOf(path: String): List<String>? {
			val segments = path.split('/').filter { it.isNotEmpty() }
			if (segments.isEmpty() || !SHARING_LINK_TYPE.matches(segments[0])) {
				return segments
			}
			val target = segments.drop(2)
			return when (segments.getOrNull(1)) {
				"r" -> target
				"s" -> listOf("sites") + target
				"t" -> listOf("teams") + target
				else -> null
			}
		}
	}
}
