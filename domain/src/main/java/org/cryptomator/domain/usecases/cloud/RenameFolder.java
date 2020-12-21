package org.cryptomator.domain.usecases.cloud;

import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.domain.usecases.ResultRenamed;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

@UseCase
class RenameFolder {

	private final CloudContentRepository cloudContentRepository;
	private final CloudFolder folder;
	private final String newName;

	public RenameFolder(CloudContentRepository cloudContentRepository, @Parameter CloudFolder folder, @Parameter String newName) {
		this.cloudContentRepository = cloudContentRepository;
		this.folder = folder;
		this.newName = newName;

	}

	public ResultRenamed<CloudFolder> execute() throws BackendException {
		CloudFolder targetFolder = cloudContentRepository.folder(folder.getParent(), newName);
		CloudFolder movedFolder = cloudContentRepository.move(folder, targetFolder);
		return new ResultRenamed<>(movedFolder, folder.getName());
	}

}
