package org.cryptomator.domain.usecases.cloud;

import org.cryptomator.domain.repository.VaultDocumentRepository;
import org.cryptomator.generator.UseCase;

@UseCase
class GetVaultDocumentCacheSize {

	private final VaultDocumentRepository vaultDocumentRepository;

	public GetVaultDocumentCacheSize(VaultDocumentRepository vaultDocumentRepository) {
		this.vaultDocumentRepository = vaultDocumentRepository;
	}

	public Long execute() {
		return vaultDocumentRepository.cacheSize();
	}
}
