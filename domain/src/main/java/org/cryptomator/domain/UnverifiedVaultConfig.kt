package org.cryptomator.domain

import java.io.Serializable
import java.net.URI

open class UnverifiedVaultConfig(open val jwt: String, open val keyId: URI, open val vaultFormat: Int) : Serializable {
	fun keyLoadingStrategy(): KeyLoadingStrategy = KeyLoadingStrategy.fromKeyId(keyId)
}

enum class KeyLoadingStrategy(private val prefix: String) {
	MASTERKEY("masterkeyfile"),
	HUB("hub+http");

	companion object {
		fun fromKeyId(keyId: URI): KeyLoadingStrategy {
			val keyIdStr = keyId.toString()
			return entries.firstOrNull { keyIdStr.startsWith(it.prefix) }
				?: throw IllegalArgumentException("Unsupported keyId prefix: $keyId")
		}
	}
}
