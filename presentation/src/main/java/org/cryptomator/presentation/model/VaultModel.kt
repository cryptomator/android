package org.cryptomator.presentation.model

import org.cryptomator.domain.Vault
import java.io.Serializable

class VaultModel(private val vault: Vault) : Serializable {

	val vaultId: Long
		get() = vault.id
	val name: String
		get() = vault.name
	val path: String
		get() = vault.path
	val isLocked: Boolean
		get() = !vault.isUnlocked
	val position: Int
		get() = vault.position
	val format: Int
		get() = vault.format
	val maxFileNameLength: Int
		get() = vault.maxFileNameLength

	fun toVault(): Vault {
		return vault
	}

	val cloudType: CloudTypeModel
		get() = CloudTypeModel.valueOf(vault.cloudType)
	val password: String?
		get() = vault.password

	override fun equals(other: Any?): Boolean {
		return vault == (other as VaultModel).toVault()
	}

	override fun hashCode(): Int {
		return vault.hashCode()
	}
}
