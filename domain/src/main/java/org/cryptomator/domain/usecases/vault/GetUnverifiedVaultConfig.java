package org.cryptomator.domain.usecases.vault;

import com.google.common.base.Optional;

import org.cryptomator.domain.UnverifiedVaultConfig;
import org.cryptomator.domain.Vault;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.NoSuchCloudFileException;
import org.cryptomator.domain.repository.CloudRepository;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

import static org.cryptomator.util.ExceptionUtil.contains;

@UseCase
public class GetUnverifiedVaultConfig {

	private final CloudRepository cloudRepository;
	private final Vault vault;

	public GetUnverifiedVaultConfig(CloudRepository cloudRepository, @Parameter Vault vault) {
		this.cloudRepository = cloudRepository;
		this.vault = vault;
	}

	public Optional<UnverifiedVaultConfig> execute() throws BackendException {
		try {
			return cloudRepository.unverifiedVaultConfig(vault);
		} catch (BackendException e) {
			if (contains(e, NoSuchCloudFileException.class)) {
				return Optional.absent();
			}
			throw e;
		}
	}

}
