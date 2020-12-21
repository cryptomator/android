package org.cryptomator.domain.usecases.vault;

import android.content.Context;

import org.cryptomator.domain.Vault;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.VaultRepository;
import org.cryptomator.generator.UseCase;
import org.cryptomator.util.SharedPreferencesHandler;
import org.cryptomator.util.crypto.BiometricAuthCryptor;

import static org.cryptomator.domain.Vault.aCopyOf;

@UseCase
class RemoveStoredVaultPasswords {

	private final VaultRepository vaultRepository;
	private final SharedPreferencesHandler sharedPreferencesHandler;
	private final Context context;

	public RemoveStoredVaultPasswords(VaultRepository vaultRepository, //
			Context context, //
			SharedPreferencesHandler sharedPreferencesHandler) {
		this.vaultRepository = vaultRepository;
		this.context = context;
		this.sharedPreferencesHandler = sharedPreferencesHandler;
	}

	public void execute() throws BackendException {
		BiometricAuthCryptor.recreateKey(context);

		sharedPreferencesHandler.changeUseBiometricAuthentication(false);

		for (Vault vault : vaultRepository.vaults()) {
			if (vault.getPassword() != null) {
				vault = aCopyOf(vault) //
						.withSavedPassword(null) //
						.build();
				vaultRepository.store(vault);
			}
		}
	}
}
