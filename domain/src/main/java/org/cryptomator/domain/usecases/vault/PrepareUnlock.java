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
class PrepareUnlock {

	private final CloudRepository cloudRepository;
	private final Vault vault;
	private final Optional<UnverifiedVaultConfig> unverifiedVaultConfig;

	public PrepareUnlock(CloudRepository cloudRepository, @Parameter Vault vault, @Parameter Optional<UnverifiedVaultConfig> unverifiedVaultConfig) {
		this.cloudRepository = cloudRepository;
		this.vault = vault;
		this.unverifiedVaultConfig = unverifiedVaultConfig;
	}

	public UnlockToken execute() throws BackendException {
		try {
			return cloudRepository.prepareUnlock(vault, unverifiedVaultConfig);
		} catch (BackendException e) {
			if (contains(e, NoSuchCloudFileException.class)) {
				throw new NoSuchVaultException(vault, e);
			}
			throw e;
		}
	}

}
