package org.cryptomator.domain.usecases.cloud;

import org.cryptomator.domain.BoxCloud;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.BoxRepository;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

@UseCase
class AuthenticateBox {

	private final BoxRepository boxRepository;
	private final String authorizationCode;

	public AuthenticateBox(BoxRepository boxRepository, @Parameter String authorizationCode) {
		this.boxRepository = boxRepository;
		this.authorizationCode = authorizationCode;
	}

	public BoxCloud execute() throws BackendException {
		return boxRepository.authenticate(authorizationCode);
	}
}
