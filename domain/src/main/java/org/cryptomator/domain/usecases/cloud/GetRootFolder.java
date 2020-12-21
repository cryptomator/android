package org.cryptomator.domain.usecases.cloud;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

@UseCase
class GetRootFolder {

	private final CloudContentRepository cloudContentRepository;
	private final Cloud cloud;

	public GetRootFolder(CloudContentRepository cloudContentRepository, @Parameter Cloud cloud) {
		this.cloudContentRepository = cloudContentRepository;
		this.cloud = cloud;
	}

	public CloudFolder execute() throws BackendException {
		return cloudContentRepository.root(cloud);
	}
}
