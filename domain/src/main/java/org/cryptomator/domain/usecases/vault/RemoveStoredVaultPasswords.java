package org.cryptomator.domain.usecases.vault;

import org.cryptomator.domain.Vault;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.VaultRepository;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

import java.util.List;

import static org.cryptomator.domain.Vault.aCopyOf;

@UseCase
class RemoveStoredVaultPasswords {

	private final List<Vault> vaults;
	private final VaultRepository vaultRepository;

	public RemoveStoredVaultPasswords(@Parameter List<Vault> vaults, VaultRepository vaultRepository) {
		this.vaults = vaults;
		this.vaultRepository = vaultRepository;
	}

	public void execute() throws BackendException {
		for (Vault vault : vaults) {
			if (vault.getPassword() != null) {
				vault = aCopyOf(vault) //
						.withSavedPassword(null, null) //
						.build();
				vaultRepository.store(vault);
			}
		}
	}
}
