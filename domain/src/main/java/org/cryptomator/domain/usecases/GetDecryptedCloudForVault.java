package org.cryptomator.domain.usecases;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.Vault;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.CloudRepository;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

@UseCase
class GetDecryptedCloudForVault {

	private final CloudRepository cloudRepository;
	private final Vault vault;

	public GetDecryptedCloudForVault(CloudRepository cloudRepository, @Parameter Vault vault) {
		this.cloudRepository = cloudRepository;
		this.vault = vault;
	}

	public Cloud execute() throws BackendException {
		return cloudRepository.decryptedViewOf(vault);
	}

}
