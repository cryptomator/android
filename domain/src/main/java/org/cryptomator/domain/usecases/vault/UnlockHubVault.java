package org.cryptomator.domain.usecases.vault;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.UnverifiedHubVaultConfig;
import org.cryptomator.domain.Vault;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.hub.HubInvalidVersionException;
import org.cryptomator.domain.repository.CloudRepository;
import org.cryptomator.domain.repository.HubRepository;
import org.cryptomator.domain.usecases.cloud.Flag;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

@UseCase
class UnlockHubVault {

	private final int HUB_MINIMUM_VERSION = 1;
	private final CloudRepository cloudRepository;
	private final Vault vault;
	private final HubRepository hubRepository;
	private final UnverifiedHubVaultConfig unverifiedVaultConfig;
	private final String accessToken;
	private volatile boolean cancelled;
	private final Flag cancelledFlag = new Flag() {
		@Override
		public boolean get() {
			return cancelled;
		}
	};

	public UnlockHubVault(CloudRepository cloudRepository, HubRepository hubRepository, @Parameter Vault vault, @Parameter UnverifiedHubVaultConfig unverifiedVaultConfig, @Parameter String accessToken) {
		this.cloudRepository = cloudRepository;
		this.vault = vault;
		this.hubRepository = hubRepository;
		this.unverifiedVaultConfig = unverifiedVaultConfig;
		this.accessToken = accessToken;
	}

	public void onCancel() {
		cancelled = true;
	}

	public Cloud execute() throws BackendException {
		HubRepository.ConfigDto config = hubRepository.getConfig(unverifiedVaultConfig, accessToken);
		if (config.getApiLevel() < HUB_MINIMUM_VERSION) {
			throw new HubInvalidVersionException("Version is " + config.getApiLevel() + " but minimum is " + HUB_MINIMUM_VERSION);
		}
		String vaultKeyJwe = hubRepository.getVaultKeyJwe(unverifiedVaultConfig, accessToken);
		HubRepository.DeviceDto device = hubRepository.getDevice(unverifiedVaultConfig, accessToken);
		return cloudRepository.unlock(vault, unverifiedVaultConfig, vaultKeyJwe, device.getUserPrivateKey(), cancelledFlag);
	}

}
