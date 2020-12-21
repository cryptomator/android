package org.cryptomator.domain.usecases.cloud;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

@UseCase
class GetUsername {

	private final CloudContentRepository cloudContentRepository;
	private final Cloud cloud;

	public GetUsername(CloudContentRepository cloudContentRepository, //
			@Parameter Cloud cloud) {
		this.cloudContentRepository = cloudContentRepository;
		this.cloud = cloud;
	}

	public String execute() throws BackendException {
		return cloudContentRepository.checkAuthenticationAndRetrieveCurrentAccount(cloud);
	}
}
