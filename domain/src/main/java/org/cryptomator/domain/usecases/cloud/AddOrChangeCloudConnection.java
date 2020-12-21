package org.cryptomator.domain.usecases.cloud;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.CloudAlreadyExistsException;
import org.cryptomator.domain.exception.FatalBackendException;
import org.cryptomator.domain.repository.CloudRepository;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

@UseCase
public class AddOrChangeCloudConnection {

	private final Cloud cloud;
	private final CloudRepository cloudRepository;

	public AddOrChangeCloudConnection(CloudRepository cloudRepository, //
			@Parameter Cloud cloud) {
		this.cloud = cloud;
		this.cloudRepository = cloudRepository;
	}

	public void execute() throws BackendException {
		if (cloudExists(cloud)) {
			throw new CloudAlreadyExistsException();
		}

		if (cloud.persistent()) {
			cloudRepository.store(cloud);
		} else {
			throw new FatalBackendException("Can't change cloud because it's not persistent");
		}
	}

	private boolean cloudExists(Cloud cloud) throws BackendException {
		for (Cloud storedCloud : cloudRepository.clouds(cloud.type())) {
			if (cloud.id() != null && cloud.id().equals(storedCloud.id())) {
				continue;
			}
			if (storedCloud.configurationMatches(cloud)) {
				return true;
			}
		}
		return false;
	}

}
