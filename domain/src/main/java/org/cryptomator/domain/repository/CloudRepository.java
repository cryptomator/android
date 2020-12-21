package org.cryptomator.domain.repository;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.CloudType;
import org.cryptomator.domain.Vault;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.usecases.vault.UnlockToken;

import java.util.List;

public interface CloudRepository {

	List<Cloud> clouds(CloudType cloudType) throws BackendException;

	List<Cloud> allClouds() throws BackendException;

	Cloud store(Cloud cloud) throws BackendException;

	void delete(Cloud cloud) throws BackendException;

	void create(CloudFolder location, CharSequence password) throws BackendException;

	Cloud decryptedViewOf(Vault vault) throws BackendException;

	boolean isVaultPasswordValid(Vault vault, CharSequence password) throws BackendException;

	void lock(Vault vault) throws BackendException;

	void changePassword(Vault vault, String oldPassword, String newPassword) throws BackendException;

	UnlockToken prepareUnlock(Vault vault) throws BackendException;

	Cloud unlock(UnlockToken token, CharSequence password) throws BackendException;

	Cloud unlock(Vault vault, CharSequence password) throws BackendException;

}
