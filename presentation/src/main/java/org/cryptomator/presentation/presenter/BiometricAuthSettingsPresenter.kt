package org.cryptomator.presentation.presenter

import android.content.Intent
import android.provider.Settings
import org.cryptomator.cryptolib.api.InvalidPassphraseException
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.Vault
import org.cryptomator.domain.di.PerView
import org.cryptomator.domain.usecases.vault.*
import org.cryptomator.generator.Callback
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.model.CloudModel
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.model.VaultModel
import org.cryptomator.presentation.ui.activity.view.BiometricAuthSettingsView
import org.cryptomator.presentation.workflow.ActivityResult
import org.cryptomator.presentation.workflow.AuthenticationExceptionHandler
import org.cryptomator.util.SharedPreferencesHandler
import timber.log.Timber
import java.util.*
import javax.inject.Inject

@PerView
class BiometricAuthSettingsPresenter @Inject constructor( //
		private val getVaultListUseCase: GetVaultListUseCase,  //
		private val saveVaultUseCase: SaveVaultUseCase,  //
		private val removeStoredVaultPasswordsUseCase: RemoveStoredVaultPasswordsUseCase,  //
		private val checkVaultPasswordUseCase: CheckVaultPasswordUseCase,  //
		private val unlockVaultUseCase: UnlockVaultUseCase,  //
		private val lockVaultUseCase: LockVaultUseCase,  //
		exceptionMappings: ExceptionHandlers,  //
		private val sharedPreferencesHandler: SharedPreferencesHandler,  //
		private val authenticationExceptionHandler: AuthenticationExceptionHandler) : Presenter<BiometricAuthSettingsView>(exceptionMappings) {

	fun loadVaultList() {
		updateVaultListView()
	}

	private fun updateVaultListView() {
		getVaultListUseCase.run(object : DefaultResultHandler<List<Vault>>() {
			override fun onSuccess(vaults: List<Vault>) {
				if (vaults.isNotEmpty()) {
					val vaultModels = vaults.mapTo(ArrayList()) { VaultModel(it) }
					view?.renderVaultList(vaultModels)
				}
			}
		})
	}

	fun updateVaultEntityWithChangedBiometricAuthSettings(vaultModel: VaultModel, useBiometricAuth: Boolean) {
		if (useBiometricAuth) {
			view?.showEnterPasswordDialog(VaultModel(vaultModel.toVault()))
		} else {
			removePasswordAndSave(vaultModel.toVault())
		}
	}

	fun verifyPassword(vaultModel: VaultModel) {
		Timber.tag("BiomtricAuthSettngsPres").i("Checking entered vault password")
		if (vaultModel.isLocked) {
			unlockVault(vaultModel)
		} else {
			checkPassword(vaultModel)
		}
	}

	private fun checkPassword(vaultModel: VaultModel) {
		view?.showProgress(ProgressModel.GENERIC)
		checkVaultPasswordUseCase //
				.withVault(vaultModel.toVault()) //
				.andPassword(vaultModel.password) //
				.run(object : DefaultResultHandler<Boolean>() {
					override fun onSuccess(passwordCorrect: Boolean) {
						if (passwordCorrect) {
							Timber.tag("BiomtricAuthSettngsPres").i("Password is correct")
							onPasswordCheckSucceeded(vaultModel)
						} else {
							Timber.tag("BiomtricAuthSettngsPres").i("Password is wrong")
							showError(InvalidPassphraseException())
						}
					}

					override fun onError(e: Throwable) {
						super.onError(e)
						Timber.tag("BiomtricAuthSettngsPres").e(e, "Password check failed")
					}
				})
	}

	private fun unlockVault(vaultModel: VaultModel) {
		view?.showProgress(ProgressModel.GENERIC)
		unlockVaultUseCase //
				.withVaultOrUnlockToken(VaultOrUnlockToken.from(vaultModel.toVault())) //
				.andPassword(vaultModel.password) //
				.run(object : DefaultResultHandler<Cloud>() {
					override fun onSuccess(cloud: Cloud) {
						Timber.tag("BiomtricAuthSettngsPres").i("Password is correct")
						onUnlockSucceeded(vaultModel)
					}

					override fun onError(e: Throwable) {
						if (!authenticationExceptionHandler.handleAuthenticationException(this@BiometricAuthSettingsPresenter, e, ActivityResultCallbacks.unlockVaultAfterAuth(vaultModel.toVault()))) {
							showError(e)
							Timber.tag("BiomtricAuthSettngsPres").e(e, "Password check failed")
						}
					}
				})
	}

	private fun onUnlockSucceeded(vaultModel: VaultModel) {
		lockVaultUseCase
				.withVault(vaultModel.toVault())
				.run(object : DefaultResultHandler<Vault>() {
					override fun onSuccess(vault: Vault) {
						super.onSuccess(vault)
						onPasswordCheckSucceeded(vaultModel)
					}

					override fun onError(e: Throwable) {
						Timber.tag("BiomtricAuthSettngsPres").e(e, "Locking vault after unlocking failed but continue to save changes")
						onPasswordCheckSucceeded(vaultModel)
					}
				})
	}

	@Callback
	fun unlockVaultAfterAuth(result: ActivityResult, vault: Vault?) {
		val cloud = result.getSingleResult(CloudModel::class.java).toCloud()
		val vaultWithUpdatedCloud = Vault.aCopyOf(vault).withCloud(cloud).build()
		unlockVault(VaultModel(vaultWithUpdatedCloud))
	}

	private fun onPasswordCheckSucceeded(vaultModel: VaultModel) {
		view?.showBiometricAuthenticationDialog(vaultModel)
	}

	fun saveVault(vault: Vault?) {
		saveVaultUseCase //
				.withVault(vault) //
				.run(object : ProgressCompletingResultHandler<Vault>() {
					override fun onSuccess(vault: Vault) {
						Timber.tag("BiomtricAuthSettngsPres").i("Saved updated vault successfully")
					}
				})
	}

	fun switchedGeneralBiometricAuthSettings(isChecked: Boolean) {
		sharedPreferencesHandler //
				.changeUseBiometricAuthentication(isChecked)
		if (isChecked) {
			loadVaultList()
		} else {
			view?.clearVaultList()
			removePasswordFromAllVaults()
		}
	}

	private fun removePasswordFromAllVaults() {
		getVaultListUseCase.run(object : DefaultResultHandler<List<Vault>>() {
			override fun onSuccess(vaults: List<Vault>) {
				vaults.filter { it.password != null }.forEach { removePasswordAndSave(it) }
			}
		})
	}

	private fun removePasswordAndSave(vault: Vault) {
		val vaultWithRemovedPassword = Vault //
				.aCopyOf(vault) //
				.withSavedPassword(null) //
				.build()
		saveVault(vaultWithRemovedPassword)
	}

	fun onSetupBiometricAuthInSystemClicked() {
		val openSecuritySettings = Intent(Settings.ACTION_SECURITY_SETTINGS)
		requestActivityResult(ActivityResultCallbacks.onSetupFingerCompleted(), openSecuritySettings)
	}

	@Callback
	fun onSetupFingerCompleted(result: ActivityResult?) {
		view?.showSetupBiometricAuthDialog()
	}

	fun onBiometricAuthKeyInvalidated(vaultModel: VaultModel?) {
		removeStoredVaultPasswordsUseCase.run(object : DefaultResultHandler<Void?>() {
			override fun onSuccess(void: Void?) {
				view?.showBiometricAuthKeyInvalidatedDialog()
			}
		})
	}

	fun onUnlockCanceled() {
		loadVaultList()
	}

	fun useConfirmationInFaceUnlockBiometricAuthentication(): Boolean {
		return sharedPreferencesHandler.useConfirmationInFaceUnlockBiometricAuthentication()
	}

	init {
		unsubscribeOnDestroy(getVaultListUseCase, saveVaultUseCase, checkVaultPasswordUseCase, removeStoredVaultPasswordsUseCase, unlockVaultUseCase)
	}
}
