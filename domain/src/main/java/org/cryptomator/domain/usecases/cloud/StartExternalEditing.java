package org.cryptomator.domain.usecases.cloud;

import org.cryptomator.domain.repository.VaultDocumentRepository;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

@UseCase
class StartExternalEditing {

	private final VaultDocumentRepository vaultDocumentRepository;
	private final String documentId;

	public StartExternalEditing(VaultDocumentRepository vaultDocumentRepository, @Parameter String documentId) {
		this.vaultDocumentRepository = vaultDocumentRepository;
		this.documentId = documentId;
	}

	public void execute() {
		vaultDocumentRepository.startExternalEditing(documentId);
	}
}
