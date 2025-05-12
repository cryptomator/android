package org.cryptomator.domain.model

import java.io.Serializable

data class DeploymentInfo(
	val vaultRemote: VaultRemote,
	val vaultInfo: VaultInfo
) : Serializable 