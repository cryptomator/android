package org.cryptomator.domain

import java.io.Serializable
import java.net.URI

class UnverifiedVaultConfig(val jwt: String, val keyId: URI, val vaultFormat: Int) : Serializable
