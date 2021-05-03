package org.cryptomator.domain.usecases.cloud;

import org.cryptomator.domain.S3Cloud;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

@UseCase
class ConnectToS3 {

	private final CloudContentRepository cloudContentRepository;
	private final S3Cloud cloud;

	public ConnectToS3(CloudContentRepository cloudContentRepository, @Parameter S3Cloud cloud) {
		this.cloudContentRepository = cloudContentRepository;
		this.cloud = cloud;
	}

	public void execute() throws BackendException {
		cloudContentRepository.checkAuthenticationAndRetrieveCurrentAccount(cloud);
	}
}
