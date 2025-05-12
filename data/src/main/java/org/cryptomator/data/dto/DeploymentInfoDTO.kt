package org.cryptomator.data.dto

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class DeploymentInfoDTO(
	@SerializedName("vaultRemote")
	val vaultRemote: VaultRemoteDTO,

	@SerializedName("vaultInfo")
	val vaultInfo: VaultInfoDTO
) : Serializable