package org.cryptomator.domain.usecases.vault;

import org.cryptomator.domain.Vault;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.VaultDocumentRepository;
import org.cryptomator.domain.repository.VaultRepository;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

import java.util.ArrayList;
import java.util.List;

@UseCase
class DeleteVaults {

	private final VaultRepository vaultRepository;
	private final VaultDocumentRepository vaultDocumentRepository;
	private final List<Vault> vaults;

	public DeleteVaults(VaultRepository vaultRepository, VaultDocumentRepository vaultDocumentRepository, @Parameter List<Vault> vaults) {
		this.vaultRepository = vaultRepository;
		this.vaultDocumentRepository = vaultDocumentRepository;
		this.vaults = vaults;
	}

	/**
	 * Removes the vaults, then lets the decrypted copies and thumbnails that only they used go, also those of the vaults
	 * removed before one failed.
	 */
	public List<Long> execute() throws BackendException {
		List<Long> ids = new ArrayList<>();
		try {
			for (Vault vault : vaults) {
				ids.add(vaultRepository.delete(vault));
			}
		} finally {
			vaultDocumentRepository.evictCopies();
		}
		return ids;
	}
}
