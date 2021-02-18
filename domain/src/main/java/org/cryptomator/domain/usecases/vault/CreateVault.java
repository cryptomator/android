package org.cryptomator.domain.usecases.vault;

import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.Vault;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.domain.repository.CloudRepository;
import org.cryptomator.domain.repository.VaultRepository;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

import static org.cryptomator.domain.Vault.aVault;

@UseCase
class CreateVault {

	private final CloudContentRepository cloudContentRepository;
	private final CloudRepository cloudRepository;
	private final VaultRepository vaultRepository;
	private final CloudFolder folder;
	private final String vaultName;
	private final String password;

	public CreateVault(CloudContentRepository cloudContentRepository, VaultRepository vaultRepository, CloudRepository cloudRepository, @Parameter CloudFolder folder, @Parameter String vaultName, @Parameter String password) {
		this.cloudContentRepository = cloudContentRepository;
		this.vaultRepository = vaultRepository;
		this.cloudRepository = cloudRepository;
		this.folder = folder;
		this.vaultName = vaultName;
		this.password = password;
	}

	public Vault execute() throws BackendException {
		CloudFolder vaultFolder = cloudContentRepository.folder(folder, vaultName);
		vaultFolder = cloudContentRepository.create(vaultFolder);
		cloudRepository.create(vaultFolder, password);
		return vaultRepository.store(aVault() //
				.thatIsNew() //
				.withNamePathAndCloudFrom(vaultFolder) //
				.withPosition(vaultRepository.vaults().size()) //
				.build());
	}
}
