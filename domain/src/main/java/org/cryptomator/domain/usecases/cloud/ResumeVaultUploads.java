package org.cryptomator.domain.usecases.cloud;

import org.cryptomator.domain.CloudFile;
import org.cryptomator.domain.Vault;
import org.cryptomator.domain.VaultPath;
import org.cryptomator.domain.repository.VaultDocumentRepository;
import org.cryptomator.generator.Optional;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

import java.util.ArrayList;
import java.util.List;

@UseCase
class ResumeVaultUploads {

	private final VaultDocumentRepository vaultDocumentRepository;
	private final Vault vault;
	private final List<CloudFile> files;

	/**
	 * @param files the files whose uploads the user asked to retry, or `null` to resume all pending uploads of the vault
	 */
	public ResumeVaultUploads(VaultDocumentRepository vaultDocumentRepository, @Parameter Vault vault, @Optional @Parameter List<CloudFile> files) {
		this.vaultDocumentRepository = vaultDocumentRepository;
		this.vault = vault;
		this.files = files;
	}

	public void execute() {
		if (files == null) {
			vaultDocumentRepository.resumeUploads(vault, null, false);
		} else {
			List<VaultPath> paths = new ArrayList<>();
			for (CloudFile file : files) {
				paths.add(vaultDocumentRepository.pathOf(file));
			}
			vaultDocumentRepository.resumeUploads(vault, paths, true);
		}
	}
}
