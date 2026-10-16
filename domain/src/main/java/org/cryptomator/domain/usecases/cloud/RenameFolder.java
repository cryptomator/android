package org.cryptomator.domain.usecases.cloud;

import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.VaultPath;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.domain.repository.VaultDocumentRepository;
import org.cryptomator.domain.usecases.ResultRenamed;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

@UseCase
class RenameFolder {

	private final CloudContentRepository cloudContentRepository;
	private final VaultDocumentRepository vaultDocumentRepository;
	private final CloudFolder folder;
	private final String newName;

	public RenameFolder(CloudContentRepository cloudContentRepository, VaultDocumentRepository vaultDocumentRepository, @Parameter CloudFolder folder, @Parameter String newName) {
		this.cloudContentRepository = cloudContentRepository;
		this.vaultDocumentRepository = vaultDocumentRepository;
		this.folder = folder;
		this.newName = newName;

	}

	public ResultRenamed<CloudFolder> execute() throws BackendException {
		VaultPath vaultFolder = vaultDocumentRepository.pathOf(folder);
		if (vaultFolder != null) {
			return new ResultRenamed<>((CloudFolder) vaultDocumentRepository.rename(vaultFolder, newName).await().getNode(), folder.getName());
		}
		CloudFolder targetFolder = cloudContentRepository.folder(folder.getParent(), newName);
		CloudFolder movedFolder = cloudContentRepository.move(folder, targetFolder);
		return new ResultRenamed<>(movedFolder, folder.getName());
	}

}
