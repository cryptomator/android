package org.cryptomator.presentation.presenter

import android.os.Handler
import androidx.biometric.BiometricManager
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.Vault
import org.cryptomator.domain.di.PerView
import org.cryptomator.domain.exception.NetworkConnectionException
import org.cryptomator.domain.exception.authentication.AuthenticationException
import org.cryptomator.domain.usecases.vault.ChangePasswordUseCase
import org.cryptomator.domain.usecases.vault.LockVaultUseCase
import org.cryptomator.domain.usecases.vault.PrepareUnlockUseCase
import org.cryptomator.domain.usecases.vault.RemoveStoredVaultPasswordsUseCase
import org.cryptomator.domain.usecases.vault.SaveVaultUseCase
import org.cryptomator.domain.usecases.vault.UnlockToken
import org.cryptomator.domain.usecases.vault.UnlockVaultUseCase
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
import org.cryptomator.presentation.workflow.ActivityResult
import org.cryptomator.presentation.workflow.AuthenticationExceptionHandler
import org.cryptomator.util.SharedPreferencesHandler
import java.io.Serializable
import javax.inject.Inject
import timber.log.Timber

@PerView
class UnlockVaultPresenter @Inject constructor(
		private val changePasswordUseCase: ChangePasswordUseCase,
		private val lockVaultUseCase: LockVaultUseCase,
		private val unlockVaultUseCase: UnlockVaultUseCase,
		private val prepareUnlockUseCase: PrepareUnlockUseCase,
		private val removeStoredVaultPasswordsUseCase: RemoveStoredVaultPasswordsUseCase,
		private val saveVaultUseCase: SaveVaultUseCase,
		private val authenticationExceptionHandler: AuthenticationExceptionHandler,
		private val sharedPreferencesHandler: SharedPreferencesHandler,
		exceptionMappings: ExceptionHandlers) : Presenter<UnlockVaultView>(exceptionMappings) {

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
			retryUnlockHandler?.removeCallbacks(null)
		}
	}

	fun setup() {
		when (intent.vaultAction()) {
			UnlockVaultIntent.VaultAction.ENCRYPT_PASSWORD -> view?.getEncryptedPasswordWithBiometricAuthentication(intent.vaultModel())
			UnlockVaultIntent.VaultAction.UNLOCK, UnlockVaultIntent.VaultAction.UNLOCK_FOR_BIOMETRIC_AUTH -> unlockVault(intent.vaultModel())
			UnlockVaultIntent.VaultAction.CHANGE_PASSWORD -> view?.showChangePasswordDialog(intent.vaultModel())
			else -> TODO("Not yet implemented")
		}
	}

	private fun unlockVault(vaultModel: VaultModel) {
		if (canUseBiometricOn(vaultModel)) {
			if (startedUsingPrepareUnlock) {
				startPrepareUnlockUseCase(vaultModel.toVault())
			}
			view?.showBiometricDialog(vaultModel)
		} else {
			startPrepareUnlockUseCase(vaultModel.toVault())
			view?.showEnterPasswordDialog(vaultModel)
		}
	}

	fun onWindowFocusChanged(hasFocus: Boolean) {
		if (hasFocus) {
			if (retryUnlockHandler != null) {
				running = false
				retryUnlockHandler?.removeCallbacks(null)
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
		return vault.password != null && BiometricManager.from(context()).canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
	}

	fun onUnlockCanceled() {
		prepareUnlockUseCase.unsubscribe()
		unlockVaultUseCase.cancel()
		finish()
	}

	fun startPrepareUnlockUseCase(vault: Vault) {
		pendingUnlock = null
		prepareUnlockUseCase //
				.withVault(vault) //
				.run(object : DefaultResultHandler<UnlockToken>() {
					override fun onSuccess(unlockToken: UnlockToken) {
						if (!startedUsingPrepareUnlock && vault.password != null) {
							doUnlock(unlockToken, vault.password)
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
									doUnlock(unlockToken, vault.password)
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

	private fun doUnlock(token: UnlockToken, password: String) {
		unlockVaultUseCase //
				.withVaultOrUnlockToken(VaultOrUnlockToken.from(token)) //
				.andPassword(password) //
				.run(object : DefaultResultHandler<Cloud>() {
					override fun onSuccess(cloud: Cloud) {
						when (intent.vaultAction()) {
							UnlockVaultIntent.VaultAction.ENCRYPT_PASSWORD, UnlockVaultIntent.VaultAction.UNLOCK_FOR_BIOMETRIC_AUTH -> {
								handleUnlockVaultSuccess(token.vault, cloud, password)
							}
							UnlockVaultIntent.VaultAction.UNLOCK -> finishWithResult(cloud)
							else -> TODO("Not yet implemented")
						}
					}
				})
	}

	private fun handleUnlockVaultSuccess(vault: Vault, cloud: Cloud, password: String) {
		lockVaultUseCase.withVault(vault).run(object : DefaultResultHandler<Vault>() {
			override fun onFinished() {
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
			else -> TODO("Not yet implemented")
		}
	}

	fun onChangePasswordClick(vaultModel: VaultModel, oldPassword: String, newPassword: String) {
		view?.showProgress(ProgressModel(ProgressStateModel.CHANGING_PASSWORD))
		changePasswordUseCase.withVault(vaultModel.toVault()) //
				.andOldPassword(oldPassword) //
				.andNewPassword(newPassword) //
				.run(object : DefaultResultHandler<Void?>() {
					override fun onSuccess(void: Void?) {
						view?.showProgress(ProgressModel.COMPLETED)
						view?.showMessage(R.string.screen_vault_list_change_password_successful)
						if (canUseBiometricOn(vaultModel)) {
							view?.getEncryptedPasswordWithBiometricAuthentication(VaultModel( //
									Vault.aCopyOf(vaultModel.toVault()) //
											.withSavedPassword(newPassword) //
											.build()))
						} else {
							finishWithResult(vaultModel)
						}
					}

					override fun onError(e: Throwable) {
						if (!authenticationExceptionHandler.handleAuthenticationException( //
										this@UnlockVaultPresenter, e,  //
										ActivityResultCallbacks.changePasswordAfterAuthentication(vaultModel.toVault(), oldPassword, newPassword))) {
							showError(e)
						}
					}
				})
	}

	@Callback
	fun changePasswordAfterAuthentication(result: ActivityResult, vault: Vault, oldPassword: String, newPassword: String) {
		val cloud = result.getSingleResult(CloudModel::class.java).toCloud()
		val vaultWithUpdatedCloud = Vault.aCopyOf(vault).withCloud(cloud).build()
		onChangePasswordClick(VaultModel(vaultWithUpdatedCloud), oldPassword, newPassword)
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

	private open class PendingUnlock(private val vault: Vault?) : Serializable {

		private var unlockToken: UnlockToken? = null
		private var password: String? = null

		fun setUnlockToken(unlockToken: UnlockToken?, presenter: UnlockVaultPresenter) {
			this.unlockToken = unlockToken
			continueIfComplete(presenter)
		}

		fun setPassword(password: String?, presenter: UnlockVaultPresenter) {
			this.password = password
			continueIfComplete(presenter)
		}

		open fun continueIfComplete(presenter: UnlockVaultPresenter) {
			unlockToken?.let { token -> password?.let { password -> presenter.doUnlock(token, password) } }
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
		unsubscribeOnDestroy(changePasswordUseCase, lockVaultUseCase, unlockVaultUseCase, prepareUnlockUseCase, removeStoredVaultPasswordsUseCase, saveVaultUseCase)
	}

}
