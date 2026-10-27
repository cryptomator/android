package org.cryptomator.domain.usecases.cloud;

import org.cryptomator.domain.CloudFile;
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
class RenameFile {

	private final CloudContentRepository cloudContentRepository;
	private final VaultDocumentRepository vaultDocumentRepository;
	private final CloudFile file;
	private final String newName;
	private final boolean optimistic;

	public RenameFile(CloudContentRepository cloudContentRepository, VaultDocumentRepository vaultDocumentRepository, @Parameter CloudFile file, @Parameter String newName, @Optional @Parameter Boolean optimistic) {
		this.cloudContentRepository = cloudContentRepository;
		this.vaultDocumentRepository = vaultDocumentRepository;
		this.file = file;
		this.newName = newName;
		this.optimistic = Boolean.TRUE.equals(optimistic);

	}

	public ResultRenamed<CloudFile> execute() throws BackendException {
		VaultPath vaultFile = vaultDocumentRepository.currentPathOf(file);
		if (vaultFile != null) {
			VaultDocumentChange<CloudNode> change = vaultDocumentRepository.rename(vaultFile, newName, optimistic);
			CloudNode renamed = optimistic ? change.cachedNode() : change.await().getNode();
			return new ResultRenamed<>((CloudFile) renamed, file.getName());
		}
		CloudFile targetFile = cloudContentRepository.file(file.getParent(), newName);
		CloudFile movedFile = cloudContentRepository.move(file, targetFile);
		return new ResultRenamed<>(movedFile, file.getName());
	}

}
