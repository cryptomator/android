package org.cryptomator.presentation.util

import android.annotation.SuppressLint
import android.content.Context
import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.biometric.BiometricFragment
import androidx.biometric.BiometricPrompt
import androidx.biometric.FingerprintDialogFragment
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

class BiometricAuthenticationMigration(
	private val callback: Callback, private val context: Context, private val useConfirmationInFaceUnlockAuth: Boolean
) {

	interface Callback {

		fun onBiometricAuthenticationMigrationFinished(vaults: List<VaultModel>)
		fun onBiometricAuthenticationFailed(vaults: List<VaultModel>)
		fun onBiometricKeyInvalidated(vaults: List<VaultModel>)
	}

	private val promptInfo = BiometricPrompt.PromptInfo.Builder().apply {
		setTitle(context.getString(R.string.dialog_biometric_migration_auth_title))
		setSubtitle(context.getString(R.string.dialog_biometric_migration_auth_message))
		setConfirmationRequired(useConfirmationInFaceUnlockAuth)
		setNegativeButtonText(context.getString(R.string.dialog_biometric_migration_auth_use_password))
	}.build()

	fun migrateVaultsPassword(fragment: Fragment, vaultModels: List<VaultModel>) {
		val decryptedVaults = mutableListOf<VaultModel>()
		val reEncryptedVaults = mutableListOf<VaultModel>()
		val vaultQueue = vaultModels.toMutableList()
		processNextVault(fragment, vaultQueue, decryptedVaults, reEncryptedVaults, vaultModels)
	}

	private fun processNextVault(
		fragment: Fragment, vaultQueue: MutableList<VaultModel>, decryptedVaults: MutableList<VaultModel>, reEncryptedVaults: MutableList<VaultModel>, allVaults: List<VaultModel>
	) {
		removeBiometricFragmentFromStack(fragment)
		when {
			vaultQueue.isNotEmpty() -> decryptUsingCbc(fragment, vaultQueue.removeAt(0), decryptedVaults, vaultQueue, reEncryptedVaults, allVaults)
			decryptedVaults.isNotEmpty() -> encryptUsingGcm(fragment, decryptedVaults.removeAt(0), vaultQueue, decryptedVaults, reEncryptedVaults, allVaults)
			else -> callback.onBiometricAuthenticationMigrationFinished(reEncryptedVaults)
		}
	}

	@SuppressLint("RestrictedApi")
	private fun removeBiometricFragmentFromStack(fragment: Fragment) {
		val fragmentManager = fragment.childFragmentManager
		fragmentManager.fragments.filter { it is BiometricFragment || it is FingerprintDialogFragment }.forEach { fragmentManager.beginTransaction().remove(it).commitNow() }
	}

	private fun decryptUsingCbc(
		fragment: Fragment, vaultModel: VaultModel, decryptedVaults: MutableList<VaultModel>, vaultQueue: MutableList<VaultModel>, reEncryptedVaults: MutableList<VaultModel>, allVaults: List<VaultModel>
	) {
		Timber.tag("BiometricAuthMigration").d("Prompt for decryption")
		handleBiometricAuthentication(fragment = fragment, cryptoMode = CryptoMode.CBC, password = vaultModel.password!!, allVaults = allVaults, onSuccess = { decryptedPassword ->
			decryptedVaults.add(
				VaultModel(
					vault = Vault.aCopyOf(vaultModel.toVault()).withSavedPassword(decryptedPassword, CryptoMode.NONE).build()
				)
			)
			processNextVault(fragment, vaultQueue, decryptedVaults, reEncryptedVaults, allVaults)
		})
	}

	private fun encryptUsingGcm(
		fragment: Fragment, vaultModel: VaultModel, vaultQueue: MutableList<VaultModel>, decryptedVaults: MutableList<VaultModel>, reEncryptedVaults: MutableList<VaultModel>, allVaults: List<VaultModel>
	) {
		Timber.tag("BiometricAuthMigration").d("Prompt for encryption")
		handleBiometricAuthentication(fragment = fragment, cryptoMode = CryptoMode.GCM, password = vaultModel.password!!, allVaults = allVaults, onSuccess = { encryptedPassword ->
			reEncryptedVaults.add(
				VaultModel(
					vault = Vault.aCopyOf(vaultModel.toVault()).withSavedPassword(encryptedPassword, CryptoMode.GCM).build()
				)
			)
			processNextVault(fragment, vaultQueue, decryptedVaults, reEncryptedVaults, allVaults)
		})
	}

	private fun handleBiometricAuthentication(
		fragment: Fragment, cryptoMode: CryptoMode, password: String, allVaults: List<VaultModel>, onSuccess: (String) -> Unit
	) {
		try {
			val biometricAuthCryptor = BiometricAuthCryptor.getInstance(context, cryptoMode)
			val authCallback = object : BiometricPrompt.AuthenticationCallback() {
				override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
					super.onAuthenticationSucceeded(result)
					Timber.tag("BiometricAuthMigration").d("Authentication succeeded")
					val cipher = result.cryptoObject?.cipher
					try {
						val processedPassword = when (cryptoMode) {
							CryptoMode.CBC -> biometricAuthCryptor.decrypt(cipher, password)
							CryptoMode.GCM -> biometricAuthCryptor.encrypt(cipher, password)
							CryptoMode.NONE -> throw IllegalStateException("CryptoMode.NONE is not allowed here")
						}
						onSuccess(processedPassword)
					} catch (e: BadPaddingException) {
						Timber.e(e, "BadPaddingException - possibly due to an invalidated key")
						callback.onBiometricKeyInvalidated(allVaults)
					}
				}

				override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
					super.onAuthenticationError(errorCode, errString)
					Timber.e("Authentication error: %s errorCode=%d", errString, errorCode)
					callback.onBiometricAuthenticationFailed(allVaults)
				}

				override fun onAuthenticationFailed() {
					super.onAuthenticationFailed()
					Timber.e("Authentication failed")
				}
			}
			val biometricPrompt = BiometricPrompt(fragment, ContextCompat.getMainExecutor(context), authCallback)
			val cryptoCipher = when (cryptoMode) {
				CryptoMode.CBC -> biometricAuthCryptor.getDecryptCipher(password)
				CryptoMode.GCM -> biometricAuthCryptor.encryptCipher
				CryptoMode.NONE -> throw IllegalStateException("CryptoMode.NONE is not allowed here")
			}
			biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cryptoCipher))
		} catch (e: KeyPermanentlyInvalidatedException) {
			Timber.e("KeyPermanentlyInvalidatedException during $cryptoMode")
			callback.onBiometricKeyInvalidated(allVaults)
		} catch (e: UnrecoverableStorageKeyException) {
			Timber.e("UnrecoverableStorageKeyException during $cryptoMode")
			callback.onBiometricKeyInvalidated(allVaults)
		}
	}
}
