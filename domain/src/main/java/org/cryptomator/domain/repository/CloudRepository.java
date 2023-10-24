package org.cryptomator.domain.repository;

import com.google.common.base.Optional;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.CloudType;
import org.cryptomator.domain.UnverifiedVaultConfig;
import org.cryptomator.domain.Vault;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.usecases.cloud.Flag;
import org.cryptomator.domain.usecases.vault.UnlockToken;

import java.util.List;

public interface CloudRepository {

	List<Cloud> clouds(CloudType cloudType) throws BackendException;

	List<Cloud> allClouds() throws BackendException;

	Cloud store(Cloud cloud) throws BackendException;

	void delete(Cloud cloud) throws BackendException;

	void create(CloudFolder location, CharSequence password) throws BackendException;

	Cloud decryptedViewOf(Vault vault) throws BackendException;

	boolean isVaultPasswordValid(Vault vault, Optional<UnverifiedVaultConfig> unverifiedVaultConfig, CharSequence password) throws BackendException;

	void lock(Vault vault) throws BackendException;

	void changePassword(Vault vault, Optional<UnverifiedVaultConfig> unverifiedVaultConfig, String oldPassword, String newPassword) throws BackendException;

	Optional<UnverifiedVaultConfig> unverifiedVaultConfig(Vault vault) throws BackendException;

	UnlockToken prepareUnlock(Vault vault, Optional<UnverifiedVaultConfig> vaultFile) throws BackendException;

	Cloud unlock(UnlockToken token, Optional<UnverifiedVaultConfig> vaultFile, CharSequence password, Flag cancelledFlag) throws BackendException;

	Cloud unlock(Vault vault, Optional<UnverifiedVaultConfig> vaultFile, CharSequence password, Flag cancelledFlag) throws BackendException;

}
