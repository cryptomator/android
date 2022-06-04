package org.cryptomator.presentation.presenter

import android.os.Handler
import androidx.biometric.BiometricManager
import com.google.common.base.Optional
import org.cryptomator.data.cloud.crypto.CryptoConstants
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.UnverifiedVaultConfig
import org.cryptomator.domain.Vault
import org.cryptomator.domain.di.PerView
import org.cryptomator.domain.exception.NetworkConnectionException
import org.cryptomator.domain.exception.authentication.AuthenticationException
import org.cryptomator.domain.usecases.vault.ChangePasswordUseCase
import org.cryptomator.domain.usecases.vault.DeleteVaultUseCase
import org.cryptomator.domain.usecases.vault.GetUnverifiedVaultConfigUseCase
import org.cryptomator.domain.usecases.vault.LockVaultUseCase
import org.cryptomator.domain.usecases.vault.PrepareUnlockUseCase
import org.cryptomator.domain.usecases.vault.RemoveStoredVaultPasswordsUseCase
import org.cryptomator.domain.usecases.vault.SaveVaultUseCase
import org.cryptomator.domain.usecases.vault.UnlockToken
import org.cryptomator.domain.usecases.vault.UnlockVaultUsingMasterkeyUseCase
import org.cryptomator.domain.usecases.vault.VaultOrUnlockToken
import org.cryptomator.generator.Callback
import org.cryptomator.generator.InjectIntent
import org.cryptomator.presentation.R
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.intent.UnlockVaultIntent
import org.cryptomator.presentation.model.CloudModel
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.model.ProgressStateModel
import org.cryptomator.presentation.model.VaultModel
import org.cryptomator.presentation.ui.activity.view.UnlockVaultView
import org.cryptomator.presentation.ui.dialog.EnterPasswordDialog
import org.cryptomator.presentation.workflow.ActivityResult
import org.cryptomator.presentation.workflow.AuthenticationExceptionHandler
import org.cryptomator.util.SharedPreferencesHandler
import java.io.Serializable
import javax.inject.Inject
import timber.log.Timber

@PerView
class UnlockVaultPresenter @Inject constructor(
	private val changePasswordUseCase: ChangePasswordUseCase,
	private val deleteVaultUseCase: DeleteVaultUseCase,
	private val getUnverifiedVaultConfigUseCase: GetUnverifiedVaultConfigUseCase,
	private val lockVaultUseCase: LockVaultUseCase,
	private val unlockVaultUsingMasterkeyUseCase: UnlockVaultUsingMasterkeyUseCase,
	private val prepareUnlockUseCase: PrepareUnlockUseCase,
	private val removeStoredVaultPasswordsUseCase: RemoveStoredVaultPasswordsUseCase,
	private val saveVaultUseCase: SaveVaultUseCase,
	private val authenticationExceptionHandler: AuthenticationExceptionHandler,
	private val sharedPreferencesHandler: SharedPreferencesHandler,
	exceptionMappings: ExceptionHandlers
) : Presenter<UnlockVaultView>(exceptionMappings) {

	private var startedUsingPrepareUnlock = false
	private var retryUnlockHandler: Handler? = null
	private var pendingUnlock: PendingUnlock? = null

	@InjectIntent
	lateinit var intent: UnlockVaultIntent

	@Volatile
	private var running: Boolean = false

	override fun destroyed() {
		super.destroyed()
		if (retryUnlockHandler != null) {
			running = false
			retryUnlockHandler?.removeCallbacksAndMessages(null)
		}
	}

	fun setup() {
		if (intent.vaultAction() == UnlockVaultIntent.VaultAction.ENCRYPT_PASSWORD) {
			view?.getEncryptedPasswordWithBiometricAuthentication(intent.vaultModel())
			return
		}

		val vault = intent.vaultModel().toVault()
		getUnverifiedVaultConfigUseCase
			.withVault(vault)
			.run(object : DefaultResultHandler<Optional<UnverifiedVaultConfig>>() {
				override fun onSuccess(unverifiedVaultConfig: Optional<UnverifiedVaultConfig>) {
					onUnverifiedVaultConfigRetrieved(unverifiedVaultConfig)
				}
				override fun onError(e: Throwable) {
					if (!authenticationExceptionHandler.handleAuthenticationException(this@UnlockVaultPresenter, e, ActivityResultCallbacks.authenticatedAfterGettingVaultConfig(vault))) {
						super.onError(e)
						finishWithResult(null)
					}
				}
			})
	}

	@Callback(dispatchResultOkOnly = false)
	fun authenticatedAfterGettingVaultConfig(result: ActivityResult, vault: Vault) {
		if (result.isResultOk) {
			val cloud = result.getSingleResult(CloudModel::class.java).toCloud()
			getUnverifiedVaultConfigUseCase
				.withVault(Vault.aCopyOf(vault).withCloud(cloud).build())
				.run(object : DefaultResultHandler<Optional<UnverifiedVaultConfig>>() {
					override fun onSuccess(unverifiedVaultConfig: Optional<UnverifiedVaultConfig>) {
						onUnverifiedVaultConfigRetrieved(unverifiedVaultConfig)
					}
					override fun onError(e: Throwable) {
						super.onError(e)
						finishWithResult(null)
					}
				})
		} else {
			view?.closeDialog()
			val error = result.getSingleResult(Throwable::class.java)
			error?.let { showError(it) }
			finishWithResult(null)
		}
	}

	private fun onUnverifiedVaultConfigRetrieved(unverifiedVaultConfig: Optional<UnverifiedVaultConfig>) {
		if (!unverifiedVaultConfig.isPresent || unverifiedVaultConfig.get().keyId.scheme == CryptoConstants.MASTERKEY_SCHEME) {
			when (intent.vaultAction()) {
				UnlockVaultIntent.VaultAction.UNLOCK, UnlockVaultIntent.VaultAction.UNLOCK_FOR_BIOMETRIC_AUTH -> {
					startedUsingPrepareUnlock = sharedPreferencesHandler.backgroundUnlockPreparation()
					pendingUnlockFor(intent.vaultModel().toVault())?.unverifiedVaultConfig = unverifiedVaultConfig.orNull()
					unlockVault(intent.vaultModel())
				}
				UnlockVaultIntent.VaultAction.CHANGE_PASSWORD -> view?.showChangePasswordDialog(intent.vaultModel(), unverifiedVaultConfig.orNull())
			}
		}
	}

	private fun unlockVault(vaultModel: VaultModel) {
		if (canUseBiometricOn(vaultModel)) {
			if (startedUsingPrepareUnlock) {
				startPrepareUnlockUseCase(vaultModel.toVault())
			}
			view?.showBiometricDialog(vaultModel)
		} else {
			view?.showEnterPasswordDialog(vaultModel)
			startPrepareUnlockUseCase(vaultModel.toVault())
		}
	}

	// FIXME why is this method not used?
	fun onWindowFocusChanged(hasFocus: Boolean) {
		if (hasFocus) {
			if (retryUnlockHandler != null) {
				running = false
				retryUnlockHandler?.removeCallbacksAndMessages(null)
			}
		}
	}

	private fun pendingUnlockFor(vault: Vault): PendingUnlock? {
		if (pendingUnlock == null) {
			pendingUnlock = PendingUnlock(vault)
		}
		return if (pendingUnlock?.belongsTo(vault) == true) {
			pendingUnlock
		} else {
			PendingUnlock.NO_OP_PENDING_UNLOCK
		}
	}

	private fun canUseBiometricOn(vault: VaultModel): Boolean {
		return vault.password != null && BiometricManager //
			.from(context()) //
			.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
	}

	fun onUnlockCanceled() {
		prepareUnlockUseCase.unsubscribe()
		unlockVaultUsingMasterkeyUseCase.cancel()
		finish()
	}

	fun startPrepareUnlockUseCase(vault: Vault) {
		prepareUnlockUseCase //
			.withVault(vault) //
			.andUnverifiedVaultConfig(Optional.fromNullable(pendingUnlockFor(intent.vaultModel().toVault())?.unverifiedVaultConfig))
			.run(object : DefaultResultHandler<UnlockToken>() {
				override fun onSuccess(unlockToken: UnlockToken) {
					if (!startedUsingPrepareUnlock && vault.password != null) {
						doUnlock(unlockToken, vault.password, pendingUnlockFor(intent.vaultModel().toVault())?.unverifiedVaultConfig)
					} else {
						unlockTokenObtained(unlockToken)
					}
				}

				override fun onError(e: Throwable) {
					if (e is AuthenticationException) {
						view?.cancelBasicAuthIfRunning()
					}
					if (!authenticationExceptionHandler.handleAuthenticationException(this@UnlockVaultPresenter, e, ActivityResultCallbacks.authenticatedAfterUnlock(vault))) {
						super.onError(e)
						if (e is NetworkConnectionException) {
							running = true
							retryUnlockHandler = Handler()
							restartUnlockUseCase(vault)
						}
					}
				}
			})
	}

	@Callback(dispatchResultOkOnly = false)
	fun authenticatedAfterUnlock(result: ActivityResult, vault: Vault) {
		if (result.isResultOk) {
			val cloud = result.getSingleResult(CloudModel::class.java).toCloud()
			if (startedUsingPrepareUnlock) {
				startPrepareUnlockUseCase(Vault.aCopyOf(vault).withCloud(cloud).build())
				if (view?.stoppedBiometricAuthDuringCloudAuthentication() == true) {
					view?.showBiometricDialog(VaultModel(vault))
				}
			} else {
				view?.showProgress(ProgressModel.GENERIC)
				startPrepareUnlockUseCase(vault)
			}
		} else {
			view?.closeDialog()
			val error = result.getSingleResult(Throwable::class.java)
			error?.let { showError(it) }
			finishWithResult(null)
		}
	}

	private fun restartUnlockUseCase(vault: Vault) {
		retryUnlockHandler?.postDelayed({
			if (running) {
				prepareUnlockUseCase //
					.withVault(vault) //
					.run(object : DefaultResultHandler<UnlockToken>() {
						override fun onSuccess(unlockToken: UnlockToken) {
							if (!startedUsingPrepareUnlock && vault.password != null) {
								doUnlock(unlockToken, vault.password, pendingUnlockFor(intent.vaultModel().toVault())?.unverifiedVaultConfig)
							} else {
								unlockTokenObtained(unlockToken)
							}
						}

						override fun onError(e: Throwable) {
							if (e is NetworkConnectionException) {
								restartUnlockUseCase(vault)
							}
						}
					})
			}
		}, 1000)
	}

	private fun unlockTokenObtained(unlockToken: UnlockToken) {
		pendingUnlockFor(unlockToken.vault)?.setUnlockToken(unlockToken, this)
	}

	fun onUnlockClick(vault: VaultModel, password: String?) {
		view?.showProgress(ProgressModel.GENERIC)
		pendingUnlockFor(vault.toVault())?.setPassword(password, this)
	}

	private fun doUnlock(token: UnlockToken, password: String, unverifiedVaultConfig: UnverifiedVaultConfig?) {
		unlockVaultUsingMasterkeyUseCase //
			.withVaultOrUnlockToken(VaultOrUnlockToken.from(token)) //
			.andUnverifiedVaultConfig(Optional.fromNullable(unverifiedVaultConfig)) //
			.andPassword(password) //
			.run(object : DefaultResultHandler<Cloud>() {
				override fun onSuccess(cloud: Cloud) {
					when (intent.vaultAction()) {
						UnlockVaultIntent.VaultAction.ENCRYPT_PASSWORD, UnlockVaultIntent.VaultAction.UNLOCK_FOR_BIOMETRIC_AUTH -> {
							handleUnlockVaultSuccess(token.vault, cloud, password)
						}
						UnlockVaultIntent.VaultAction.UNLOCK -> finishWithResult(cloud)
					}
				}

				override fun onError(e: Throwable) {
					super.onError(e)
					// finish in case of biometric auth, otherwise show error in dialog
					if (view?.isShowingDialog(EnterPasswordDialog::class) == false) {
						finishWithResult(null)
					}
				}
			})
	}

	private fun handleUnlockVaultSuccess(vault: Vault, cloud: Cloud, password: String) {
		lockVaultUseCase.withVault(vault).run(object : DefaultResultHandler<Vault>() {
			override fun onSuccess(vault: Vault) {
				finishWithResultAndExtra(cloud, PASSWORD, password)
			}
		})

	}

	fun startedUsingPrepareUnlock(): Boolean {
		return startedUsingPrepareUnlock
	}

	fun useConfirmationInFaceUnlockBiometricAuthentication(): Boolean {
		return sharedPreferencesHandler.useConfirmationInFaceUnlockBiometricAuthentication()
	}

	fun onBiometricKeyInvalidated() {
		removeStoredVaultPasswordsUseCase.run(object : DefaultResultHandler<Void?>() {
			override fun onSuccess(void: Void?) {
				view?.showBiometricAuthKeyInvalidatedDialog()
			}

			override fun onError(e: Throwable) {
				Timber.tag("VaultListPresenter").e(e, "Error while removing vault passwords")
				finishWithResult(null)
			}
		})
	}

	fun onBiometricAuthenticationSucceeded(vaultModel: VaultModel) {
		when (intent.vaultAction()) {
			UnlockVaultIntent.VaultAction.ENCRYPT_PASSWORD -> finishWithResult(vaultModel)
			UnlockVaultIntent.VaultAction.UNLOCK, UnlockVaultIntent.VaultAction.UNLOCK_FOR_BIOMETRIC_AUTH -> {
				if (startedUsingPrepareUnlock) {
					onUnlockClick(vaultModel, vaultModel.password)
				} else {
					view?.showProgress(ProgressModel.GENERIC)
					startPrepareUnlockUseCase(vaultModel.toVault())
				}
			}
			UnlockVaultIntent.VaultAction.CHANGE_PASSWORD -> {
				saveVaultUseCase //
					.withVault(vaultModel.toVault()) //
					.run(object : DefaultResultHandler<Vault>() {
						override fun onSuccess(vault: Vault) {
							finishWithResult(vaultModel)
						}
					})
			}
		}
	}

	fun onChangePasswordClick(vaultModel: VaultModel, unverifiedVaultConfig: UnverifiedVaultConfig?, oldPassword: String, newPassword: String) {
		view?.showProgress(ProgressModel(ProgressStateModel.CHANGING_PASSWORD))
		changePasswordUseCase.withVault(vaultModel.toVault()) //
			.andUnverifiedVaultConfig(Optional.fromNullable(unverifiedVaultConfig)) //
			.andOldPassword(oldPassword) //
			.andNewPassword(newPassword) //
			.run(object : DefaultResultHandler<Void?>() {
				override fun onSuccess(void: Void?) {
					view?.showProgress(ProgressModel.COMPLETED)
					view?.showMessage(R.string.screen_vault_list_change_password_successful)
					if (canUseBiometricOn(vaultModel)) {
						view?.getEncryptedPasswordWithBiometricAuthentication(
							VaultModel( //
								Vault.aCopyOf(vaultModel.toVault()) //
									.withSavedPassword(newPassword) //
									.build()
							)
						)
					} else {
						finishWithResult(vaultModel)
					}
				}

				override fun onError(e: Throwable) {
					if (!authenticationExceptionHandler.handleAuthenticationException( //
							this@UnlockVaultPresenter, e,  //
							ActivityResultCallbacks.changePasswordAfterAuthentication(vaultModel.toVault(), unverifiedVaultConfig, oldPassword, newPassword)
						)
					) {
						showError(e)
					}
				}
			})
	}

	@Callback
	fun changePasswordAfterAuthentication(result: ActivityResult, vault: Vault, unverifiedVaultConfig: UnverifiedVaultConfig, oldPassword: String, newPassword: String) {
		val cloud = result.getSingleResult(CloudModel::class.java).toCloud()
		val vaultWithUpdatedCloud = Vault.aCopyOf(vault).withCloud(cloud).build()
		onChangePasswordClick(VaultModel(vaultWithUpdatedCloud), unverifiedVaultConfig, oldPassword, newPassword)
	}

	fun saveVaultAfterChangePasswordButFailedBiometricAuth(vault: Vault) {
		Timber.tag("UnlockVaultPresenter").e("Save vault without password because biometric auth failed after changing vault password")
		saveVaultUseCase //
			.withVault(vault) //
			.run(object : DefaultResultHandler<Vault>() {
				override fun onSuccess(vault: Vault) {
					finishWithResult(vault)
				}
			})
	}

	fun onDeleteMissingVaultClicked(vault: Vault) {
		deleteVaultUseCase //
			.withVault(vault) //
			.run(object : DefaultResultHandler<Long>() {
				override fun onSuccess(vaultId: Long) {
					finishWithResult(null)
				}
			})
	}

	fun onCancelMissingVaultClicked(vault: Vault) {
		finishWithResult(null)
	}

	fun onChangePasswordCanceled() {
		finishWithResult(null)
	}

	private open class PendingUnlock(private val vault: Vault?) : Serializable {

		private var unlockToken: UnlockToken? = null
		private var password: String? = null

		var unverifiedVaultConfig: UnverifiedVaultConfig? = null

		fun setUnlockToken(unlockToken: UnlockToken?, presenter: UnlockVaultPresenter) {
			this.unlockToken = unlockToken
			continueIfComplete(presenter)
		}

		fun setPassword(password: String?, presenter: UnlockVaultPresenter) {
			this.password = password
			continueIfComplete(presenter)
		}

		open fun continueIfComplete(presenter: UnlockVaultPresenter) {
			unlockToken?.let { token -> password?.let { password -> presenter.doUnlock(token, password, unverifiedVaultConfig) } }
		}

		fun belongsTo(vault: Vault): Boolean {
			return vault == this.vault
		}

		companion object {

			val NO_OP_PENDING_UNLOCK: PendingUnlock = object : PendingUnlock(null) {
				override fun continueIfComplete(presenter: UnlockVaultPresenter) {
					// empty
				}
			}
		}
	}

	companion object {

		const val PASSWORD = "password"
	}

	init {
		unsubscribeOnDestroy( //
			changePasswordUseCase,  //
			deleteVaultUseCase, //
			getUnverifiedVaultConfigUseCase, //
			lockVaultUseCase, //
			unlockVaultUsingMasterkeyUseCase, //
			prepareUnlockUseCase, //
			removeStoredVaultPasswordsUseCase, //
			saveVaultUseCase
		)
	}

}
