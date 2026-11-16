package org.cryptomator.presentation.util

import okhttp3.HttpUrl.Companion.toHttpUrl

class WebDavUrl {

	companion object {

		fun switchedToHttps(url: String): String {
			return url.toHttpUrl().newBuilder().scheme("https").build().toString().removeSuffix("/")
		}
	}
}
