package org.cryptomator.data.cloud.microsoftgraph

import java.net.URI

class SharepointSiteId {

	companion object {

		/**
		 * Address of a SharePoint site URL for Graph's `/sites/` endpoint: the host for the root site, `host:/path` otherwise.
		 */
		fun of(siteUrl: String): String {
			val uri = URI(siteUrl)
			val path = uri.rawPath.orEmpty()
			return if (path.isEmpty()) uri.host else "${uri.host}:$path"
		}
	}
}
