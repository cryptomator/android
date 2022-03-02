package org.cryptomator.domain.usecases.vault;

import org.cryptomator.domain.Vault;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.VaultRepository;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

import java.util.ArrayList;
import java.util.List;

@UseCase
class DeleteVaults {

	private final VaultRepository vaultRepository;
	private final List<Vault> vaults;

	public DeleteVaults(VaultRepository vaultRepository, @Parameter List<Vault> vaults) {
		this.vaultRepository = vaultRepository;
		this.vaults = vaults;
	}

	public List<Long> execute() throws BackendException {
		List<Long> ids = new ArrayList<>();
		for (Vault vault : vaults) {
			ids.add(vaultRepository.delete(vault));
		}
		return ids;
	}
}
