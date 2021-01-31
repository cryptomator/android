package org.cryptomator.domain.usecases.vault;

import org.cryptomator.domain.Vault;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.VaultRepository;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

import java.util.List;

@UseCase
class MoveVaultPosition {

	private final VaultRepository vaultRepository;
	private final int fromPosition;
	private final int toPosition;

	public MoveVaultPosition(VaultRepository vaultRepository, @Parameter Integer fromPosition, @Parameter Integer toPosition) {
		this.vaultRepository = vaultRepository;
		this.fromPosition = fromPosition;
		this.toPosition = toPosition;
	}

	public List<Vault> execute() throws BackendException {
		List<Vault> vaults = MoveVaultHelper.Companion.updateVaultPosition(fromPosition, toPosition, vaultRepository);
		return MoveVaultHelper.Companion.updateVaultsInDatabase(vaults, vaultRepository);
	}
}
