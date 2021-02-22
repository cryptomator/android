package org.cryptomator.domain.usecases.vault;

import org.cryptomator.domain.Vault
import org.cryptomator.domain.repository.VaultRepository
import java.util.*

class MoveVaultHelper {

	companion object {
		fun updateVaultPosition(fromPosition: Int, toPosition: Int, vaultRepository: VaultRepository): List<Vault> {
			val vaults = vaultRepository.vaults()

			vaults.sortWith(VaultComparator())

			if (fromPosition < toPosition) {
				for (i in fromPosition until toPosition) {
					Collections.swap(vaults, i, i + 1)
				}
			} else {
				for (i in fromPosition downTo toPosition + 1) {
					Collections.swap(vaults, i, i - 1)
				}
			}
			return reorderVaults(vaults)
		}

		private fun reorderVaults(vaults: MutableList<Vault>) : List<Vault> {
			for (i in 0 until vaults.size) {
				vaults[i] = Vault.aCopyOf(vaults[i]).withPosition(i).build()
			}
			return vaults;
		}

		fun reorderVaults(vaultRepository: VaultRepository) : List<Vault> {
			val vaults = vaultRepository.vaults()
			vaults.sortWith(VaultComparator())
			return reorderVaults(vaults)
		}

		fun updateVaultsInDatabase(vaults: List<Vault>, vaultRepository: VaultRepository): List<Vault> {
			vaults.forEach { vault -> vaultRepository.store(vault) }
			return vaultRepository.vaults()
		}
	}

	internal class VaultComparator : Comparator<Vault> {
		override fun compare(o1: Vault, o2: Vault): Int {
			return o1.position - o2.position
		}
	}
}
