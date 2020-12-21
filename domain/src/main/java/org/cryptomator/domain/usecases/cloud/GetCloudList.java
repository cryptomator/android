package org.cryptomator.domain.usecases.cloud;

import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.CloudNode;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

import java.util.List;

@UseCase
class GetCloudList {

	private final CloudContentRepository cloudContentRepository;
	private final CloudFolder folder;

	public GetCloudList(CloudContentRepository cloudContentRepository, @Parameter CloudFolder folder) {
		this.cloudContentRepository = cloudContentRepository;
		this.folder = folder;
	}

	public List<CloudNode> execute() throws BackendException {
		return cloudContentRepository.list(folder);
	}

}
