package org.cryptomator.domain.usecases.cloud;

import org.cryptomator.domain.Vault;
import org.cryptomator.domain.repository.VaultDocumentRepository;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

@UseCase
class ResumeVaultUploads {

	private final VaultDocumentRepository vaultDocumentRepository;
	private final Vault vault;

	public ResumeVaultUploads(VaultDocumentRepository vaultDocumentRepository, @Parameter Vault vault) {
		this.vaultDocumentRepository = vaultDocumentRepository;
		this.vault = vault;
	}

	public void execute() {
		vaultDocumentRepository.resumeUploads(vault);
	}
}
