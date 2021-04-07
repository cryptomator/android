package org.cryptomator.domain.usecases.vault;

import org.cryptomator.domain.UnverifiedVaultConfig;
import org.cryptomator.domain.Vault;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.CloudRepository;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;
import org.cryptomator.util.Optional;

@UseCase
class CheckVaultPassword {

	private final CloudRepository cloudRepository;
	private final Vault vault;
	private final String password;
	private final Optional<UnverifiedVaultConfig> unverifiedVaultConfig;

	public CheckVaultPassword(CloudRepository cloudRepository, @Parameter Vault vault, @Parameter String password, @Parameter Optional<UnverifiedVaultConfig> unverifiedVaultConfig) {
		this.cloudRepository = cloudRepository;
		this.vault = vault;
		this.password = password;
		this.unverifiedVaultConfig = unverifiedVaultConfig;
	}

	public Boolean execute() throws BackendException {
		return cloudRepository.isVaultPasswordValid(vault, unverifiedVaultConfig, password);
	}

}
