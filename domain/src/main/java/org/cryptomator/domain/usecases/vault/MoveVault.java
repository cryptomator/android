package org.cryptomator.domain.usecases.vault;

import org.cryptomator.domain.Vault;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.VaultRepository;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

import java.util.List;

@UseCase
class MoveVault {

	private final VaultRepository vaultRepository;
	private final int from;
	private final int to;

	public MoveVault(VaultRepository vaultRepository, @Parameter Integer from, @Parameter Integer to) {
		this.vaultRepository = vaultRepository;
		this.from = from;
		this.to = to;
	}

	public List<Vault> execute() throws BackendException {
		List<Vault> vaults = MoveVaultHelper.Companion.updateVaultPosition(from, to, vaultRepository);
		return MoveVaultHelper.Companion.updateVaultsInDatabase(vaults, vaultRepository);
	}
}
