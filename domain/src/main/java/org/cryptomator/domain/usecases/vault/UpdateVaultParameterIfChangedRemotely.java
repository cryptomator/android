package org.cryptomator.domain.usecases.vault;

import org.cryptomator.domain.Vault;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.VaultRepository;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

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
		if(oldVault.getFormat() == vault.getFormat() && oldVault.getShorteningThreshold() == vault.getShorteningThreshold()) {
			return vault;
		} else {
			return vaultRepository.store(vault);
		}
	}

}
