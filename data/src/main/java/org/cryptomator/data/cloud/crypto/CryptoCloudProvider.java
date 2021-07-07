package org.cryptomator.data.cloud.crypto;

import com.google.common.base.Optional;

import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.UnverifiedVaultConfig;
import org.cryptomator.domain.Vault;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.usecases.cloud.Flag;
import org.cryptomator.domain.usecases.vault.UnlockToken;

public interface CryptoCloudProvider {

	void create(CloudFolder location, CharSequence password) throws BackendException;

	Vault unlock(Vault vault, Optional<UnverifiedVaultConfig> unverifiedVaultConfig, CharSequence password, Flag cancelledFlag) throws BackendException;

	UnlockToken createUnlockToken(Vault vault, Optional<UnverifiedVaultConfig> unverifiedVaultConfig) throws BackendException;

	Vault unlock(UnlockToken token, Optional<UnverifiedVaultConfig> unverifiedVaultConfig, CharSequence password, Flag cancelledFlag) throws BackendException;

	boolean isVaultPasswordValid(Vault vault, Optional<UnverifiedVaultConfig> unverifiedVaultConfig, CharSequence password) throws BackendException;

	void lock(Vault vault);

	void changePassword(Vault vault, Optional<UnverifiedVaultConfig> unverifiedVaultConfig, String oldPassword, String newPassword) throws BackendException;

}
