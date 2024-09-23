package org.cryptomator.domain.usecases.vault;

import org.cryptomator.domain.Vault;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.VaultRepository;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

import java.util.ArrayList;
import java.util.List;

@UseCase
class SaveVaults {

	private final VaultRepository vaultRepository;
	private final List<Vault> vaults;

	public SaveVaults(VaultRepository vaultRepository, @Parameter List<Vault> vaults) {
		this.vaultRepository = vaultRepository;
		this.vaults = vaults;
	}

	public List<Vault> execute() throws BackendException {
		List<Vault> storedVaults = new ArrayList<>();
		for (Vault vault : vaults) {
			storedVaults.add(vaultRepository.store(vault));
		}
		return storedVaults;
	}

}
