package org.cryptomator.data.dto

import com.google.gson.annotations.SerializedName

class VaultInfoDTO {

	@SerializedName("lastVaultUpdated")
	var lastVaultUpdated: Long = 0

	@SerializedName("vaultMK")
	var vaultMK: String? = null

	@SerializedName("vaultData")
	var vaultData: String? = null

	@SerializedName("vaultPKI")
	var vaultPKI: String? = null
}