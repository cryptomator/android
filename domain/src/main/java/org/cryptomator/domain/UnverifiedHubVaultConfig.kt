package org.cryptomator.domain

import java.net.URI

class UnverifiedHubVaultConfig(
	override val jwt: String,
	override val keyId: URI,
	override val vaultFormat: Int,
	val clientId: String,
	val authEndpoint: URI,
	val tokenEndpoint: URI,
	val apiBaseUrl: URI
) : UnverifiedVaultConfig(jwt, keyId, vaultFormat) {

	fun vaultId(): String {
		assert(keyId.scheme.startsWith("hub+"))
		val path = keyId.path
		return path.substring(path.lastIndexOf('/') + 1)
	}
}
