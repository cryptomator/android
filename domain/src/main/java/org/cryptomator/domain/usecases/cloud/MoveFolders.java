package org.cryptomator.domain.usecases.cloud;

import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.CloudNode;
import org.cryptomator.domain.VaultPath;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.domain.repository.VaultDocumentChange;
import org.cryptomator.domain.repository.VaultDocumentRepository;
import org.cryptomator.generator.Optional;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

import java.util.ArrayList;
import java.util.List;

@UseCase
class MoveFolders {

	private final CloudContentRepository cloudContentRepository;
	private final VaultDocumentRepository vaultDocumentRepository;
	private final List<CloudFolder> sourceFolders;
	private final CloudFolder parent;
	private final boolean optimistic;

	MoveFolders(CloudContentRepository cloudContentRepository, //
			VaultDocumentRepository vaultDocumentRepository, //
			@Parameter List<CloudFolder> sourceFolders, //
			@Parameter CloudFolder parent, //
			@Optional @Parameter Boolean optimistic) {
		this.cloudContentRepository = cloudContentRepository;
		this.vaultDocumentRepository = vaultDocumentRepository;
		this.sourceFolders = sourceFolders;
		this.parent = parent;
		this.optimistic = Boolean.TRUE.equals(optimistic);
	}

	public List<CloudFolder> execute() throws BackendException {
		VaultPath vaultFolder = vaultDocumentRepository.pathOf(parent);
		List<CloudFolder> resultFolders = new ArrayList<>();
		for (CloudFolder sourceFolder : sourceFolders) {
			if (vaultFolder != null) {
				VaultPath source = vaultDocumentRepository.pathOf(sourceFolder);
				VaultDocumentChange<CloudNode> change = vaultDocumentRepository.move(source, vaultFolder, optimistic);
				CloudNode moved = optimistic ? change.cachedNode() : change.await().getNode();
				if (moved != null) {
					resultFolders.add((CloudFolder) moved);
				}
			} else {
				CloudFolder targetFolder = cloudContentRepository.folder(parent, sourceFolder.getName());
				resultFolders.add(cloudContentRepository.move(sourceFolder, targetFolder));
			}
		}
		return resultFolders;
	}
}
