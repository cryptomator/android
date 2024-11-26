package org.cryptomator.domain

import java.io.Serializable
import java.net.URI

open class UnverifiedVaultConfig(open val jwt: String, open val keyId: URI, open val vaultFormat: Int) : Serializable
