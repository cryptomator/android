package org.cryptomator.domain.usecases.vault;

import org.cryptomator.domain.Vault;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.VaultRepository;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

/**
 * Stores what an unlock found out about the vault if it differs from what is stored: its format and shortening threshold,
 * and whether it is a Hub vault with a paid license, by which the write access of the locked vault is judged.
 */
@UseCase
class UpdateVaultParameterIfChangedRemotely {

	private final VaultRepository vaultRepository;
	private final Vault vault;

	public UpdateVaultParameterIfChangedRemotely(VaultRepository vaultRepository, @Parameter Vault vault) {
		this.vaultRepository = vaultRepository;
		this.vault = vault;
	}

	public Vault execute() throws BackendException {
		Vault oldVault = vaultRepository.load(vault.getId());
		if (oldVault.getFormat() == vault.getFormat() //
				&& oldVault.getShorteningThreshold() == vault.getShorteningThreshold() //
				&& oldVault.isHubVault() == vault.isHubVault() //
				&& oldVault.hasHubPaidLicense() == vault.hasHubPaidLicense()) {
			return vault;
		} else {
			return vaultRepository.store(vault);
		}
	}

}
