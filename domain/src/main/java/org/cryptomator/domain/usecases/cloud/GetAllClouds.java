package org.cryptomator.domain.usecases.cloud;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.CloudRepository;
import org.cryptomator.generator.UseCase;

import java.util.List;

@UseCase
class GetAllClouds {

	private final CloudRepository cloudRepository;

	public GetAllClouds(CloudRepository cloudRepository) {
		this.cloudRepository = cloudRepository;
	}

	public List<Cloud> execute() throws BackendException {
		return cloudRepository.allClouds();
	}
}
