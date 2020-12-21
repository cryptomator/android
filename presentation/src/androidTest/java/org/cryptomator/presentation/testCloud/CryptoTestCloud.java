package org.cryptomator.presentation.testCloud;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.Vault;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.FatalBackendException;
import org.cryptomator.presentation.di.component.ApplicationComponent;

import static org.cryptomator.domain.Vault.aVault;

public class CryptoTestCloud extends TestCloud {

	private final static String VAULT_PASSWORD = "password";
	private final static String VAULT_NAME = "testVault";

	@Override
	public Cloud getInstance(ApplicationComponent appComponent) {
		throw new IllegalStateException();
	}

	public Cloud getInstance(ApplicationComponent appComponent, Cloud testCloud, CloudFolder rootFolder) {
		try {
			CloudFolder vaultFolder = appComponent //
					.cloudContentRepository() //
					.folder(rootFolder, VAULT_NAME);

			Vault vault = aVault() //
					.thatIsNew() //
					.withCloud(testCloud) //
					.withNamePathAndCloudFrom(vaultFolder) //
					.build();

			cleanup(appComponent, vault, vaultFolder);

			vaultFolder = appComponent.cloudContentRepository().create(vaultFolder);
			appComponent.cloudRepository().create(vaultFolder, VAULT_PASSWORD);
			vault = appComponent.vaultRepository().store(vault);

			return appComponent.cloudRepository().unlock(vault, VAULT_PASSWORD);
		} catch (BackendException e) {
			throw new AssertionError(e);
		}
	}

	private void cleanup(ApplicationComponent appComponent, Vault vault, CloudFolder vaultFolder) {
		try {
			appComponent.cloudContentRepository().delete(vaultFolder);
		} catch (BackendException | FatalBackendException e) {
		}

		try {
			appComponent.vaultRepository().vaults().forEach(vaultInRepo -> {
				if (vaultInRepo.getName().equals(vault.getName()) //
						&& vaultInRepo.getPath().equals(vault.getPath())) {
					try {
						appComponent.vaultRepository().delete(vaultInRepo);
					} catch (BackendException e) {
						throw new AssertionError(e);
					}
				}
			});
		} catch (FatalBackendException | BackendException e) {
		}
	}

	@Override
	public String toString() {
		return "CryptoTestCloud";
	}
}
