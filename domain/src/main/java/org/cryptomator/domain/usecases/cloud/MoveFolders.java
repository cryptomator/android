package org.cryptomator.domain.usecases.cloud;

import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

import java.util.ArrayList;
import java.util.List;

@UseCase
class MoveFolders {

	private final CloudContentRepository cloudContentRepository;
	private final List<CloudFolder> sourceFolders;
	private final CloudFolder parent;

	MoveFolders(CloudContentRepository cloudContentRepository, //
			@Parameter List<CloudFolder> sourceFolders, //
			@Parameter CloudFolder parent) {
		this.cloudContentRepository = cloudContentRepository;
		this.sourceFolders = sourceFolders;
		this.parent = parent;
	}

	public List<CloudFolder> execute() throws BackendException {
		List<CloudFolder> resultFolders = new ArrayList<>();
		for (CloudFolder sourceFolder : sourceFolders) {
			CloudFolder targetFolder = cloudContentRepository.folder(parent, sourceFolder.getName());
			resultFolders.add(cloudContentRepository.move(sourceFolder, targetFolder));
		}
		return resultFolders;
	}
}
