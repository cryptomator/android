package org.cryptomator.domain.usecases.vault;

import org.cryptomator.domain.Vault;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.CloudRepository;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

@UseCase
class CheckVaultPassword {

	private final CloudRepository cloudRepository;
	private final Vault vault;
	private final String password;

	public CheckVaultPassword(CloudRepository cloudRepository, @Parameter Vault vault, @Parameter String password) {
		this.cloudRepository = cloudRepository;
		this.vault = vault;
		this.password = password;
	}

	public Boolean execute() throws BackendException {
		return cloudRepository.isVaultPasswordValid(vault, password);
	}

}
