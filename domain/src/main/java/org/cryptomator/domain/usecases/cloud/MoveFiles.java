package org.cryptomator.domain.usecases.cloud;

import org.cryptomator.domain.CloudFile;
import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.VaultPath;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.domain.repository.VaultDocumentRepository;
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

	MoveFiles(CloudContentRepository cloudContentRepository, VaultDocumentRepository vaultDocumentRepository, @Parameter List<CloudFile> sourceFiles, @Parameter CloudFolder parent) {
		this.cloudContentRepository = cloudContentRepository;
		this.vaultDocumentRepository = vaultDocumentRepository;
		this.sourceFiles = sourceFiles;
		this.parent = parent;
	}

	public List<CloudFile> execute() throws BackendException {
		VaultPath vaultFolder = vaultDocumentRepository.pathOf(parent);
		List<CloudFile> resultFiles = new ArrayList<>();
		for (CloudFile sourceFile : sourceFiles) {
			if (vaultFolder != null) {
				resultFiles.add((CloudFile) vaultDocumentRepository.move(vaultDocumentRepository.pathOf(sourceFile), vaultFolder).await().getNode());
			} else {
				CloudFile targetFile = cloudContentRepository.file(parent, sourceFile.getName());
				resultFiles.add(cloudContentRepository.move(sourceFile, targetFile));
			}
		}
		return resultFiles;
	}
}
