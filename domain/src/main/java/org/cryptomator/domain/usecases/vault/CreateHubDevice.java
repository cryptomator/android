package org.cryptomator.domain.usecases.vault;

import org.cryptomator.domain.UnverifiedHubVaultConfig;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.HubRepository;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;


@UseCase
class CreateHubDevice {

	private final HubRepository hubRepository;
	private final UnverifiedHubVaultConfig unverifiedVaultConfig;
	private final String accessToken;
	private final String deviceName;
	private final String setupCode;

	public CreateHubDevice(HubRepository hubRepository, @Parameter UnverifiedHubVaultConfig unverifiedVaultConfig, @Parameter String accessToken, @Parameter String deviceName, @Parameter String setupCode) {
		this.hubRepository = hubRepository;
		this.unverifiedVaultConfig = unverifiedVaultConfig;
		this.accessToken = accessToken;
		this.deviceName = deviceName;
		this.setupCode = setupCode;
	}

	public void execute() throws BackendException {
		HubRepository.UserDto user = hubRepository.getUser(unverifiedVaultConfig, accessToken);
		hubRepository.createDevice(unverifiedVaultConfig, accessToken, deviceName, setupCode, user.getPrivateKey());
	}

}
