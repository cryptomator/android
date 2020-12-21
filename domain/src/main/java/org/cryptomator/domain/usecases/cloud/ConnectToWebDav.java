package org.cryptomator.domain.usecases.cloud;

import org.cryptomator.domain.WebDavCloud;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

@UseCase
class ConnectToWebDav {

	private final CloudContentRepository cloudContentRepository;
	private final WebDavCloud cloud;

	public ConnectToWebDav(CloudContentRepository cloudContentRepository, @Parameter WebDavCloud cloud) {
		this.cloudContentRepository = cloudContentRepository;
		this.cloud = cloud;
	}

	public void execute() throws BackendException {
		cloudContentRepository.checkAuthenticationAndRetrieveCurrentAccount(cloud);
	}
}
