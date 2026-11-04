package org.cryptomator.domain.usecases.cloud;

import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.VaultDocumentRepository;
import org.cryptomator.domain.repository.VaultListing;
import org.cryptomator.generator.Optional;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

@UseCase
class ListVaultFolder {

	private final VaultDocumentRepository vaultDocumentRepository;
	private final CloudFolder folder;
	private final boolean forceRefresh;

	public ListVaultFolder(VaultDocumentRepository vaultDocumentRepository, @Parameter CloudFolder folder, @Optional @Parameter Boolean forceRefresh) {
		this.vaultDocumentRepository = vaultDocumentRepository;
		this.folder = folder;
		this.forceRefresh = Boolean.TRUE.equals(forceRefresh);
	}

	public VaultListing execute() throws BackendException {
		return vaultDocumentRepository.list(folder, forceRefresh);
	}
}
