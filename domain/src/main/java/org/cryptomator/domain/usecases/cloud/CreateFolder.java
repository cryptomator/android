package org.cryptomator.domain.usecases.cloud;

import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

@UseCase
class CreateFolder {

	private final CloudContentRepository cloudContentRepository;
	private final CloudFolder parent;
	private final String folderName;

	public CreateFolder(CloudContentRepository cloudContentRepository, @Parameter CloudFolder parent, @Parameter String folderName) {
		this.cloudContentRepository = cloudContentRepository;
		this.parent = parent;
		this.folderName = folderName;
	}

	public CloudFolder execute() throws BackendException {
		CloudFolder toCreate = cloudContentRepository.folder(parent, folderName);
		return cloudContentRepository.create(toCreate);
	}
}
