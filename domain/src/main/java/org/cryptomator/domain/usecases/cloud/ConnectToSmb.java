package org.cryptomator.domain.usecases.cloud;

import org.cryptomator.domain.SmbCloud;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

@UseCase
public class ConnectToSmb {

	private final CloudContentRepository cloudContentRepository;
	private final SmbCloud cloud;

	public ConnectToSmb(CloudContentRepository cloudContentRepository, @Parameter SmbCloud cloud) {
		this.cloudContentRepository = cloudContentRepository;
		this.cloud = cloud;
	}

	public void execute() throws BackendException {
		cloudContentRepository.checkAuthenticationAndRetrieveCurrentAccount(cloud);
	}
}
