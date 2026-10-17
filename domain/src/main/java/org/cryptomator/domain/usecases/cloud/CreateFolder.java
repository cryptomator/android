package org.cryptomator.domain.usecases.cloud;

import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.VaultPath;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.domain.repository.VaultDocumentChange;
import org.cryptomator.domain.repository.VaultDocumentRepository;
import org.cryptomator.generator.Optional;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

@UseCase
class CreateFolder {

	private final CloudContentRepository cloudContentRepository;
	private final VaultDocumentRepository vaultDocumentRepository;
	private final CloudFolder parent;
	private final String folderName;
	private final boolean optimistic;

	public CreateFolder(CloudContentRepository cloudContentRepository, VaultDocumentRepository vaultDocumentRepository, @Parameter CloudFolder parent, @Parameter String folderName, @Optional @Parameter Boolean optimistic) {
		this.cloudContentRepository = cloudContentRepository;
		this.vaultDocumentRepository = vaultDocumentRepository;
		this.parent = parent;
		this.folderName = folderName;
		this.optimistic = Boolean.TRUE.equals(optimistic);
	}

	public CloudFolder execute() throws BackendException {
		VaultPath vaultFolder = vaultDocumentRepository.pathOf(parent);
		if (vaultFolder != null) {
			VaultDocumentChange<CloudFolder> change = vaultDocumentRepository.createFolder(vaultFolder, folderName, optimistic);
			return optimistic ? change.cachedNode() : change.await().getNode();
		}
		CloudFolder toCreate = cloudContentRepository.folder(parent, folderName);
		return cloudContentRepository.create(toCreate);
	}
}
