package org.cryptomator.domain.usecases.cloud;

import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.CloudNode;
import org.cryptomator.domain.VaultPath;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.domain.repository.VaultDocumentChange;
import org.cryptomator.domain.repository.VaultDocumentRepository;
import org.cryptomator.domain.usecases.ResultRenamed;
import org.cryptomator.generator.Optional;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

@UseCase
class RenameFolder {

	private final CloudContentRepository cloudContentRepository;
	private final VaultDocumentRepository vaultDocumentRepository;
	private final CloudFolder folder;
	private final String newName;
	private final boolean optimistic;

	public RenameFolder(CloudContentRepository cloudContentRepository, VaultDocumentRepository vaultDocumentRepository, @Parameter CloudFolder folder, @Parameter String newName, @Optional @Parameter Boolean optimistic) {
		this.cloudContentRepository = cloudContentRepository;
		this.vaultDocumentRepository = vaultDocumentRepository;
		this.folder = folder;
		this.newName = newName;
		this.optimistic = Boolean.TRUE.equals(optimistic);

	}

	public ResultRenamed<CloudFolder> execute() throws BackendException {
		VaultPath vaultFolder = vaultDocumentRepository.currentPathOf(folder);
		if (vaultFolder != null) {
			VaultDocumentChange<CloudNode> change = vaultDocumentRepository.rename(vaultFolder, newName, optimistic);
			CloudNode renamed = optimistic ? change.cachedNode() : change.await().getNode();
			return new ResultRenamed<>((CloudFolder) renamed, folder.getName());
		}
		CloudFolder targetFolder = cloudContentRepository.folder(folder.getParent(), newName);
		CloudFolder movedFolder = cloudContentRepository.move(folder, targetFolder);
		return new ResultRenamed<>(movedFolder, folder.getName());
	}

}
