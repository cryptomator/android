package org.cryptomator.domain.usecases.vault;

import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.Vault;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.NoSuchCloudFileException;
import org.cryptomator.domain.exception.NoSuchVaultException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.domain.repository.CloudRepository;
import org.cryptomator.domain.repository.VaultRepository;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

import static org.cryptomator.domain.Vault.aCopyOf;
import static org.cryptomator.util.ExceptionUtil.contains;

@UseCase
class RenameVault {

	private final CloudContentRepository cloudContentRepository;
	private final CloudRepository cloudRepository;
	private final VaultRepository vaultRepository;
	private Vault vault;
	private final String newVaultName;

	public RenameVault(CloudContentRepository cloudContentRepository, CloudRepository cloudRepository, VaultRepository vaultRepository, @Parameter Vault vault, @Parameter String newVaultName) {
		this.cloudContentRepository = cloudContentRepository;
		this.vaultRepository = vaultRepository;
		this.cloudRepository = cloudRepository;
		this.vault = vault;
		this.newVaultName = newVaultName;
	}

	public Vault execute() throws BackendException {
		try {
			CloudFolder vaultLocation = cloudContentRepository.resolve(vault.getCloud(), vault.getPath());
			CloudFolder vaultLocationAfterRename = cloudContentRepository.folder(vaultLocation.getParent(), newVaultName);
			cloudContentRepository.move(vaultLocation, vaultLocationAfterRename);

			if (vault.isUnlocked()) {
				cloudRepository.lock(vault);
				vault = Vault.aCopyOf(vault) //
						.withUnlocked(false).build();
			}
			Vault renamedVault = aCopyOf(vault) //
					.withNamePathAndCloudFrom(vaultLocationAfterRename) //
					.build();
			return vaultRepository.store(renamedVault);
		} catch (BackendException e) {
			if (contains(e, NoSuchCloudFileException.class)) {
				throw new NoSuchVaultException(vault, e);
			}
			throw e;
		}
	}
}
