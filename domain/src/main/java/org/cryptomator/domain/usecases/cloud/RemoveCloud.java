package org.cryptomator.domain.usecases.cloud;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.CloudRepository;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

@UseCase
class RemoveCloud {

	private final Cloud cloud;
	private final CloudRepository cloudRepository;

	public RemoveCloud(CloudRepository cloudRepository, @Parameter Cloud cloud) {
		this.cloud = cloud;
		this.cloudRepository = cloudRepository;
	}

	public void execute() throws BackendException {
		cloudRepository.delete(cloud);
	}
}
