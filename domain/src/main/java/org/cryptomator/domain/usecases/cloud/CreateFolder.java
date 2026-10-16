package org.cryptomator.domain.usecases.cloud;

import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.VaultPath;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.domain.repository.VaultDocumentRepository;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

@UseCase
class CreateFolder {

	private final CloudContentRepository cloudContentRepository;
	private final VaultDocumentRepository vaultDocumentRepository;
	private final CloudFolder parent;
	private final String folderName;

	public CreateFolder(CloudContentRepository cloudContentRepository, VaultDocumentRepository vaultDocumentRepository, @Parameter CloudFolder parent, @Parameter String folderName) {
		this.cloudContentRepository = cloudContentRepository;
		this.vaultDocumentRepository = vaultDocumentRepository;
		this.parent = parent;
		this.folderName = folderName;
	}

	public CloudFolder execute() throws BackendException {
		VaultPath vaultFolder = vaultDocumentRepository.pathOf(parent);
		if (vaultFolder != null) {
			return vaultDocumentRepository.createFolder(vaultFolder, folderName).await().getNode();
		}
		CloudFolder toCreate = cloudContentRepository.folder(parent, folderName);
		return cloudContentRepository.create(toCreate);
	}
}
