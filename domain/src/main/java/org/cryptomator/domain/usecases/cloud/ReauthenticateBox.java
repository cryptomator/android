package org.cryptomator.domain.usecases.cloud;

import org.cryptomator.domain.BoxCloud;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.BoxRepository;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

@UseCase
class ReauthenticateBox {

	private final BoxRepository boxRepository;
	private final BoxCloud cloud;
	private final String authorizationCode;

	public ReauthenticateBox(BoxRepository boxRepository, @Parameter BoxCloud cloud, @Parameter String authorizationCode) {
		this.boxRepository = boxRepository;
		this.cloud = cloud;
		this.authorizationCode = authorizationCode;
	}

	public BoxCloud execute() throws BackendException {
		return boxRepository.reauthenticate(cloud, authorizationCode);
	}
}
