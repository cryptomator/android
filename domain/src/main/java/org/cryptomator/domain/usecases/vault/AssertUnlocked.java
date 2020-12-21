package org.cryptomator.domain.usecases.vault;

import org.cryptomator.domain.Vault;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.VaultRepository;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

@UseCase
class AssertUnlocked {

	private final Vault vault;
	private final VaultRepository vaultRepository;

	public AssertUnlocked(VaultRepository vaultRepository, @Parameter Vault vault) {
		this.vaultRepository = vaultRepository;
		this.vault = vault;
	}

	public void execute() throws BackendException {
		vaultRepository.assertUnlocked(vault);
	}

}
