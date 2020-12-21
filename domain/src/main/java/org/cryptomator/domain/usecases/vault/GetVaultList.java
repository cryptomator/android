package org.cryptomator.domain.usecases.vault;

import org.cryptomator.domain.Vault;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.VaultRepository;
import org.cryptomator.generator.UseCase;

import java.util.List;

@UseCase
class GetVaultList {

	private final VaultRepository vaultRepository;

	public GetVaultList(VaultRepository vaultRepository) {
		this.vaultRepository = vaultRepository;
	}

	public List<Vault> execute() throws BackendException {
		return vaultRepository.vaults();
	}

}
