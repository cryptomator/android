package org.cryptomator.domain

enum class WebDavProviderVariant(val provider: WebDavProvider, val url: String) {

	ONE_AND_ONE_DSL(WebDavProvider.ONE_AND_ONE, "https://sd2dav.1und1.de"), //
	ONE_AND_ONE_WEBHOSTING(WebDavProvider.ONE_AND_ONE, "https://webdav.office.1und1.de")
}
