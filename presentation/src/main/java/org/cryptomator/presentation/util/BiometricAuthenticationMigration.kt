package org.cryptomator.presentation.util

import android.content.Context
import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import org.cryptomator.domain.Vault
import org.cryptomator.presentation.R
import org.cryptomator.presentation.model.VaultModel
import org.cryptomator.util.crypto.BiometricAuthCryptor
import org.cryptomator.util.crypto.CryptoMode
import org.cryptomator.util.crypto.UnrecoverableStorageKeyException
import javax.crypto.BadPaddingException
import timber.log.Timber

class BiometricAuthenticationMigration(val callback: Callback, val context: Context, private val useConfirmationInFaceUnlockAuth: Boolean) {

	interface Callback {

		fun onBiometricAuthenticationMigrationFinished(vaults: List<VaultModel>)
		fun onBiometricAuthenticationFailed(vaults: List<VaultModel>)
		fun onBiometricKeyInvalidated(vaults: List<VaultModel>)

	}

	companion object {

		private lateinit var promptInfo: BiometricPrompt.PromptInfo

	}

	fun migrateVaultsPassword(fragment: Fragment, vaultModels: List<VaultModel>) {
		val decryptedVaults = mutableListOf<VaultModel>()
		val vaultQueue = ArrayDeque(vaultModels)

		promptInfo = BiometricPrompt.PromptInfo.Builder() //
			.setTitle(context.getString(R.string.dialog_biometric_auth_title)) //
			.setSubtitle(context.getString(R.string.dialog_biometric_auth_message)) //
			.setConfirmationRequired(useConfirmationInFaceUnlockAuth) //
			.setNegativeButtonText(context.getString(R.string.dialog_biometric_auth_use_password)) //
			.build()

		// Start processing the queue
		processNextVault(fragment, vaultQueue, decryptedVaults)
	}

	private fun processNextVault(fragment: Fragment, vaultQueue: ArrayDeque<VaultModel>, decryptedVaults: MutableList<VaultModel>) {
		if (vaultQueue.isEmpty()) {
			encryptUsingGcm(fragment, decryptedVaults)
		} else {
			val currentVault = vaultQueue.removeFirst() // Get the next vault to process
			decryptVaultPassword(fragment, currentVault, decryptedVaults, vaultQueue)
		}
	}

	private fun decryptVaultPassword(fragment: Fragment, vaultModel: VaultModel, decryptedVaults: MutableList<VaultModel>, vaultQueue: ArrayDeque<VaultModel>) {
		Timber.tag("BiometricAuthenticationMigration").d("Show decrypt biometric auth prompt")
		try {
			val biometricAuthCryptorCBC = BiometricAuthCryptor.getInstance(context, CryptoMode.CBC)
			val authCallback = object : BiometricPrompt.AuthenticationCallback() {
				override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
					super.onAuthenticationSucceeded(result)
					Timber.tag("BiometricAuthenticationMigration").d("Authentication finished successfully")
					val cipher = result.cryptoObject?.cipher
					try {
						val decryptedPassword = biometricAuthCryptorCBC.decrypt(cipher, vaultModel.password)
						val decryptedVaultModel = VaultModel(
							Vault.aCopyOf(vaultModel.toVault())
								.withSavedPassword(decryptedPassword, CryptoMode.NONE)
								.build()
						)
						// Add the decrypted vault to the list
						decryptedVaults.add(decryptedVaultModel)
						// Process the next vault
						processNextVault(fragment, vaultQueue, decryptedVaults)
					} catch (e: BadPaddingException) {
						Timber.tag("BiometricAuthenticationMigration").i(
							e,
							"Recover from BadPaddingException which can be thrown on some devices if the key in the keystore is invalidated e.g. due to a fingerprint added because of an upstream error in Android, see #400 for more info"
						)
						callback.onBiometricKeyInvalidated(decryptedVaults)
					}
				}

				override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
					super.onAuthenticationError(errorCode, errString)
					Timber.tag("BiometricAuthenticationMigration").e(String.format("Authentication error: %s errorCode=%d", errString, errorCode))
					callback.onBiometricAuthenticationFailed(decryptedVaults)
				}

				override fun onAuthenticationFailed() {
					super.onAuthenticationFailed()
					Timber.tag("BiometricAuthenticationMigration").e("Authentication failed")
				}
			}
			val biometricPrompt = BiometricPrompt(fragment, ContextCompat.getMainExecutor(context), authCallback)
			try {
				val cryptoCipher = biometricAuthCryptorCBC.getDecryptCipher(vaultModel.password)
				biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cryptoCipher))
			} catch (e: KeyPermanentlyInvalidatedException) {
				callback.onBiometricKeyInvalidated(decryptedVaults)
			}
		} catch (e: UnrecoverableStorageKeyException) {
			callback.onBiometricKeyInvalidated(listOf(vaultModel))
		}
	}

	private fun encryptUsingGcm(fragment: Fragment, vaultModels: List<VaultModel>) {
		Timber.tag("BiometricAuthenticationMigration").d("Show encrypt biometric auth prompt")
		val biometricAuthCryptorGCM: BiometricAuthCryptor
		try {
			biometricAuthCryptorGCM = BiometricAuthCryptor.getInstance(context, CryptoMode.GCM)
		} catch (e: UnrecoverableStorageKeyException) {
			return callback.onBiometricKeyInvalidated(vaultModels)
		}
		val authCallback = object : BiometricPrompt.AuthenticationCallback() {
			override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
				super.onAuthenticationSucceeded(result)
				Timber.tag("BiometricAuthenticationMigration").d("Authentication finished successfully")
				val cipher = result.cryptoObject?.cipher
				try {
					val gcmEncryptedVaults = vaultModels.map { vaultModel ->
						val encryptedPassword = biometricAuthCryptorGCM.encrypt(cipher, vaultModel.password)
						VaultModel(
							Vault //
								.aCopyOf(vaultModel.toVault()) //
								.withSavedPassword(encryptedPassword, CryptoMode.GCM) //
								.build()
						)
					}
					callback.onBiometricAuthenticationMigrationFinished(gcmEncryptedVaults)
				} catch (e: BadPaddingException) {
					Timber.tag("BiometricAuthenticationMigration").i(
						e,
						"Recover from BadPaddingException which can be thrown on some devices if the key in the keystore is invalidated e.g. due to a fingerprint added because of an upstream error in Android, see #400 for more info"
					)
					callback.onBiometricKeyInvalidated(vaultModels)
				}
			}

			override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
				super.onAuthenticationError(errorCode, errString)
				Timber.tag("BiometricAuthenticationMigration").e(String.format("Authentication error: %s errorCode=%d", errString, errorCode))
				callback.onBiometricAuthenticationFailed(vaultModels)
			}

			override fun onAuthenticationFailed() {
				super.onAuthenticationFailed()
				Timber.tag("BiometricAuthenticationMigration").e("Authentication failed")
			}
		}
		val biometricPrompt = BiometricPrompt(fragment, ContextCompat.getMainExecutor(context), authCallback)
		try {
			val cryptoCipher = biometricAuthCryptorGCM.encryptCipher
			biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cryptoCipher))
		} catch (e: KeyPermanentlyInvalidatedException) {
			callback.onBiometricKeyInvalidated(vaultModels)
		}
	}
}
