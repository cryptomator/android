package org.cryptomator.presentation.util

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class ServerUrl {

	companion object {

		fun parse(server: String): String? {
			val serverWithScheme = if (server.contains("://")) server else "https://$server"
			return serverWithScheme.toHttpUrlOrNull()?.takeIf { it.query == null && it.fragment == null && it.username.isEmpty() && it.password.isEmpty() }?.toString()
		}
	}
}
