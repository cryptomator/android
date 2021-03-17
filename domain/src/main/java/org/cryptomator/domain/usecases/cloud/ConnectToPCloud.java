package org.cryptomator.domain.usecases.cloud;

import org.cryptomator.domain.PCloud;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

@UseCase
class ConnectToPCloud {

	private final CloudContentRepository cloudContentRepository;
	private final PCloud cloud;

	public ConnectToPCloud(CloudContentRepository cloudContentRepository, @Parameter PCloud cloud) {
		this.cloudContentRepository = cloudContentRepository;
		this.cloud = cloud;
	}

	public void execute() throws BackendException {
		cloudContentRepository.checkAuthenticationAndRetrieveCurrentAccount(cloud);
	}
}
