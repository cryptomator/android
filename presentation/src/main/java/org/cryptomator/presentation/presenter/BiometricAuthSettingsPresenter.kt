package org.cryptomator.presentation.presenter

import android.content.Intent
import android.provider.Settings
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.Vault
import org.cryptomator.domain.di.PerView
import org.cryptomator.domain.usecases.vault.GetVaultListUseCase
import org.cryptomator.domain.usecases.vault.LockVaultUseCase
import org.cryptomator.domain.usecases.vault.SaveVaultUseCase
import org.cryptomator.generator.Callback
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.intent.Intents
import org.cryptomator.presentation.intent.UnlockVaultIntent
import org.cryptomator.presentation.model.VaultModel
import org.cryptomator.presentation.ui.activity.view.BiometricAuthSettingsView
import org.cryptomator.presentation.workflow.ActivityResult
import org.cryptomator.util.SharedPreferencesHandler
import java.util.ArrayList
import javax.inject.Inject
import timber.log.Timber

@PerView
class BiometricAuthSettingsPresenter @Inject constructor( //
	private val getVaultListUseCase: GetVaultListUseCase,  //
	private val saveVaultUseCase: SaveVaultUseCase,  //
	private val lockVaultUseCase: LockVaultUseCase,  //
	exceptionMappings: ExceptionHandlers,  //
	private val sharedPreferencesHandler: SharedPreferencesHandler
) : Presenter<BiometricAuthSettingsView>(exceptionMappings) {

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
			verifyPassword(vaultModel)
		} else {
			removePasswordAndSave(vaultModel.toVault())
		}
	}

	private fun verifyPassword(vaultModel: VaultModel) {
		Timber.tag("BiomtricAuthSettngsPres").i("Checking entered vault password")
		if (vaultModel.isLocked) {
			requestActivityResult( //
				ActivityResultCallbacks.vaultUnlockedBiometricAuthPres(vaultModel), //
				Intents.unlockVaultIntent().withVaultModel(vaultModel).withVaultAction(UnlockVaultIntent.VaultAction.UNLOCK_FOR_BIOMETRIC_AUTH)
			)
		} else {
			lockVaultUseCase
				.withVault(vaultModel.toVault())
				.run(object : DefaultResultHandler<Vault>() {
					override fun onSuccess(vault: Vault) {
						super.onSuccess(vault)
						requestActivityResult( //
							ActivityResultCallbacks.vaultUnlockedBiometricAuthPres(vaultModel), //
							Intents.unlockVaultIntent().withVaultModel(vaultModel).withVaultAction(UnlockVaultIntent.VaultAction.UNLOCK_FOR_BIOMETRIC_AUTH)
						)
					}
				})
		}
	}

	@Callback
	fun vaultUnlockedBiometricAuthPres(result: ActivityResult, vaultModel: VaultModel) {
		val cloud = result.intent().getSerializableExtra(SINGLE_RESULT) as Cloud
		val password = result.intent().getStringExtra(UnlockVaultPresenter.PASSWORD)
		val vault = Vault.aCopyOf(vaultModel.toVault()).withCloud(cloud).withSavedPassword(password).build()
		requestActivityResult( //
			ActivityResultCallbacks.encryptVaultPassword(vaultModel), //
			Intents.unlockVaultIntent().withVaultModel(VaultModel(vault)).withVaultAction(UnlockVaultIntent.VaultAction.ENCRYPT_PASSWORD))
	}

	@Callback
	fun encryptVaultPassword(result: ActivityResult, vaultModel: VaultModel) {
		val tmpVault = result.intent().getSerializableExtra(SINGLE_RESULT) as VaultModel
		val vault = Vault.aCopyOf(vaultModel.toVault()).withSavedPassword(tmpVault.password).build()
		saveVault(vault)
	}

	private fun saveVault(vault: Vault?) {
		saveVaultUseCase //
			.withVault(vault) //
			.run(object : ProgressCompletingResultHandler<Vault>() {
				override fun onSuccess(vault: Vault) {
					Timber.tag("BiomtricAuthSettngsPres").i("Saved updated vault successfully")
				}
			})
	}

	fun switchedGeneralBiometricAuthSettings(isChecked: Boolean) {
		sharedPreferencesHandler.changeUseBiometricAuthentication(isChecked)
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
		requestActivityResult(ActivityResultCallbacks.onSetupBiometricAuthInSystemCompleted(), openSecuritySettings)
	}

	@Callback
	fun onSetupBiometricAuthInSystemCompleted(result: ActivityResult?) {
		view?.showSetupBiometricAuthDialog()
	}

	init {
		unsubscribeOnDestroy(getVaultListUseCase, saveVaultUseCase, lockVaultUseCase)
	}
}
