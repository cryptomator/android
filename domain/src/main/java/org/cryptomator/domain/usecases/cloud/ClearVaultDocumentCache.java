package org.cryptomator.domain.usecases.cloud;

import org.cryptomator.domain.repository.VaultDocumentRepository;
import org.cryptomator.generator.UseCase;

@UseCase
class ClearVaultDocumentCache {

	private final VaultDocumentRepository vaultDocumentRepository;

	public ClearVaultDocumentCache(VaultDocumentRepository vaultDocumentRepository) {
		this.vaultDocumentRepository = vaultDocumentRepository;
	}

	public void execute() {
		vaultDocumentRepository.clearCache();
	}
}
