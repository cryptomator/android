package org.cryptomator.domain.model

data class VaultInfo(
    val lastVaultUpdated: Long,
    val vaultMK: String,
    val vaultData: String,
    val vaultPKI: String
) 