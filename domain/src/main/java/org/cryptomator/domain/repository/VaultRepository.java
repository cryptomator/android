package org.cryptomator.domain.repository;

import org.cryptomator.domain.Vault;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.MissingCryptorException;

import java.util.List;

public interface VaultRepository {

	List<Vault> vaults() throws BackendException;

	Vault store(Vault vault) throws BackendException;

	Long delete(Vault vault) throws BackendException;

	Vault load(Long id) throws BackendException;

	void assertUnlocked(Vault vault) throws MissingCryptorException;

	/**
	 * @return {@code vault} as it was unlocked, or {@code null} if it is locked. Only the unlocked vault knows what the unlock
	 * found out, e.g. whether a Hub vault has a paid license.
	 */
	Vault unlockedVault(Vault vault);

}
