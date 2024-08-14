package org.cryptomator.domain.usecases.vault;

import org.cryptomator.domain.Vault;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.VaultRepository;
import org.cryptomator.generator.UseCase;
import org.cryptomator.util.crypto.CryptoMode;

import java.util.List;
import java.util.stream.Collectors;

@UseCase
class ListCBCEncryptedPasswordVaults {

	private final VaultRepository vaultRepository;

	public ListCBCEncryptedPasswordVaults(VaultRepository vaultRepository) {
		this.vaultRepository = vaultRepository;
	}

	public List<Vault> execute() throws BackendException {
		return vaultRepository //
				.vaults() //
				.stream() //
				.filter(vault -> vault.getPasswordCryptoMode() != null && vault.getPasswordCryptoMode().equals(CryptoMode.CBC)) //
				.collect(Collectors.toUnmodifiableList());
	}

}
