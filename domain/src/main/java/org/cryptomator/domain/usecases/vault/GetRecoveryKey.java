package org.cryptomator.domain.usecases.vault;

import com.google.common.base.Optional;

import org.cryptomator.domain.UnverifiedVaultConfig;
import org.cryptomator.domain.Vault;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.NoSuchCloudFileException;
import org.cryptomator.domain.exception.NoSuchVaultException;
import org.cryptomator.domain.repository.CloudRepository;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

import static org.cryptomator.util.ExceptionUtil.contains;

@UseCase
class GetRecoveryKey {

	private final CloudRepository cloudRepository;
	private final Vault vault;
	private final Optional<UnverifiedVaultConfig> unverifiedVaultConfig;
	private final String password;

	public GetRecoveryKey(CloudRepository cloudRepository, //
			@Parameter Vault vault, //
			@Parameter Optional<UnverifiedVaultConfig> unverifiedVaultConfig, //
			@Parameter String password) {
		this.cloudRepository = cloudRepository;
		this.vault = vault;
		this.unverifiedVaultConfig = unverifiedVaultConfig;
		this.password = password;
	}

	public String execute() throws BackendException {
		try {
			return cloudRepository.recoveryKey(vault, unverifiedVaultConfig, password);
		} catch (BackendException e) {
			if (contains(e, NoSuchCloudFileException.class)) {
				throw new NoSuchVaultException(vault, e);
			}
			throw e;
		}
	}
}
