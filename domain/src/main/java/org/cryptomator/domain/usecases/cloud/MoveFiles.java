package org.cryptomator.domain.usecases.cloud;

import org.cryptomator.domain.CloudFile;
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
class MoveFiles {

	private final CloudContentRepository cloudContentRepository;
	private final VaultDocumentRepository vaultDocumentRepository;
	private final List<CloudFile> sourceFiles;
	private final CloudFolder parent;
	private final boolean optimistic;

	MoveFiles(CloudContentRepository cloudContentRepository, VaultDocumentRepository vaultDocumentRepository, @Parameter List<CloudFile> sourceFiles, @Parameter CloudFolder parent, //
			@Optional @Parameter Boolean optimistic) {
		this.cloudContentRepository = cloudContentRepository;
		this.vaultDocumentRepository = vaultDocumentRepository;
		this.sourceFiles = sourceFiles;
		this.parent = parent;
		this.optimistic = Boolean.TRUE.equals(optimistic);
	}

	public List<CloudFile> execute() throws BackendException {
		List<CloudFile> resultFiles = new ArrayList<>();
		for (CloudFile sourceFile : sourceFiles) {
			// looked up for each file, since the folder may be renamed while the files before are moved
			VaultPath vaultFolder = vaultDocumentRepository.currentPathOf(parent);
			if (vaultFolder != null) {
				VaultPath source = vaultDocumentRepository.currentPathOf(sourceFile);
				VaultDocumentChange<CloudNode> change = vaultDocumentRepository.move(source, vaultFolder, optimistic);
				CloudNode moved = optimistic ? change.cachedNode() : change.await().getNode();
				if (moved != null) {
					resultFiles.add((CloudFile) moved);
				}
			} else {
				CloudFile targetFile = cloudContentRepository.file(parent, sourceFile.getName());
				resultFiles.add(cloudContentRepository.move(sourceFile, targetFile));
			}
		}
		return resultFiles;
	}
}
