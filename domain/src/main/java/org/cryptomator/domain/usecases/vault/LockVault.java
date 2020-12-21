package org.cryptomator.domain.usecases.vault;

import org.cryptomator.domain.Vault;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.CloudRepository;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

@UseCase
class LockVault {

	private final CloudRepository cloudRepository;
	private final Vault vault;

	public LockVault(CloudRepository cloudRepository, @Parameter Vault vault) {
		this.cloudRepository = cloudRepository;
		this.vault = vault;
	}

	public Vault execute() throws BackendException {
		cloudRepository.lock(vault);
		return Vault.aCopyOf(vault) //
				.withUnlocked(false).build();
	}
}
