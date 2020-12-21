package org.cryptomator.domain.usecases.cloud;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.CloudType;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.CloudRepository;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

import java.util.List;

@UseCase
class GetClouds {

	private final CloudRepository cloudRepository;
	private final CloudType cloudType;

	public GetClouds(CloudRepository cloudRepository, @Parameter CloudType cloudType) {
		this.cloudRepository = cloudRepository;
		this.cloudType = cloudType;
	}

	public List<Cloud> execute() throws BackendException {
		return cloudRepository.clouds(cloudType);
	}
}
