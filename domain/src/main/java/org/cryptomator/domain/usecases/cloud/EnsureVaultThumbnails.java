package org.cryptomator.domain.usecases.cloud;

import org.cryptomator.domain.CloudFile;
import org.cryptomator.domain.repository.VaultDocumentRepository;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

import java.util.List;

@UseCase
class EnsureVaultThumbnails {

	private final VaultDocumentRepository vaultDocumentRepository;
	private final List<CloudFile> files;

	public EnsureVaultThumbnails(VaultDocumentRepository vaultDocumentRepository, @Parameter List<CloudFile> files) {
		this.vaultDocumentRepository = vaultDocumentRepository;
		this.files = files;
	}

	public void execute() {
		vaultDocumentRepository.ensureThumbnails(files);
	}
}
