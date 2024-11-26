package org.cryptomator.presentation.presenter

import android.content.Intent
import android.net.Uri
import android.os.Handler
import androidx.biometric.BiometricManager
import com.google.common.base.Optional
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import org.cryptomator.data.cloud.crypto.CryptoConstants
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.UnverifiedHubVaultConfig
import org.cryptomator.domain.UnverifiedVaultConfig
import org.cryptomator.domain.Vault
import org.cryptomator.domain.di.PerView
import org.cryptomator.domain.exception.NetworkConnectionException
import org.cryptomator.domain.exception.authentication.AuthenticationException
import org.cryptomator.domain.exception.hub.HubAuthenticationFailedException
import org.cryptomator.domain.exception.hub.HubDeviceSetupRequiredException
import org.cryptomator.domain.exception.hub.HubLicenseUpgradeRequiredException
import org.cryptomator.domain.exception.hub.HubUserSetupRequiredException
import org.cryptomator.domain.exception.hub.HubVaultAccessForbiddenException
import org.cryptomator.domain.exception.hub.HubVaultIsArchivedException
import org.cryptomator.domain.exception.hub.HubVaultOperationNotSupportedException
import org.cryptomator.domain.usecases.vault.ChangePasswordUseCase
import org.cryptomator.domain.usecases.vault.CreateHubDeviceUseCase
import org.cryptomator.domain.usecases.vault.DeleteVaultUseCase
import org.cryptomator.domain.usecases.vault.GetUnverifiedVaultConfigUseCase
import org.cryptomator.domain.usecases.vault.LockVaultUseCase
import org.cryptomator.domain.usecases.vault.PrepareUnlockUseCase
import org.cryptomator.domain.usecases.vault.RemoveStoredVaultPasswordsAndDisableBiometricAuthUseCase
import org.cryptomator.domain.usecases.vault.SaveVaultUseCase
import org.cryptomator.domain.usecases.vault.UnlockHubVaultUseCase
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
import org.cryptomator.util.crypto.CryptoMode
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
	private val unlockHubVaultUseCase: UnlockHubVaultUseCase,
	private val createHubDeviceUseCase: CreateHubDeviceUseCase,
	private val removeStoredVaultPasswordsAndDisableBiometricAuthUseCase: RemoveStoredVaultPasswordsAndDisableBiometricAuthUseCase,
	private val saveVaultUseCase: SaveVaultUseCase,
	private val authenticationExceptionHandler: AuthenticationExceptionHandler,
	private val sharedPreferencesHandler: SharedPreferencesHandler,
	exceptionMappings: ExceptionHandlers
) : Presenter<UnlockVaultView>(exceptionMappings) {

	private var startedUsingPrepareUnlock = false
	private var retryUnlockHandler: Handler? = null
	private var pendingUnlock: PendingUnlock? = null
	private var hubAuthService: AuthorizationService? = null

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
		hubAuthService?.dispose()
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
					onUnverifiedVaultConfigRetrieved(unverifiedVaultConfig, vault)
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
						onUnverifiedVaultConfigRetrieved(unverifiedVaultConfig, vault)
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

	private fun onUnverifiedVaultConfigRetrieved(unverifiedVaultConfig: Optional<UnverifiedVaultConfig>, vault: Vault) {
		if (!unverifiedVaultConfig.isPresent || unverifiedVaultConfig.get().keyId.scheme == CryptoConstants.MASTERKEY_SCHEME) {
			when (intent.vaultAction()) {
				UnlockVaultIntent.VaultAction.UNLOCK, UnlockVaultIntent.VaultAction.UNLOCK_FOR_BIOMETRIC_AUTH -> {
					startedUsingPrepareUnlock = sharedPreferencesHandler.backgroundUnlockPreparation()
					pendingUnlockFor(intent.vaultModel().toVault())?.unverifiedVaultConfig = unverifiedVaultConfig.orNull()
					unlockVault(intent.vaultModel())
				}
				UnlockVaultIntent.VaultAction.CHANGE_PASSWORD -> view?.showChangePasswordDialog(intent.vaultModel(), unverifiedVaultConfig.orNull())
				else -> {}
			}
		} else if (unverifiedVaultConfig.isPresent && unverifiedVaultConfig.get().keyId.scheme.startsWith(CryptoConstants.HUB_SCHEME)) {
			when (intent.vaultAction()) {
				UnlockVaultIntent.VaultAction.UNLOCK -> {
					val unverifiedHubVaultConfig = unverifiedVaultConfig.get() as UnverifiedHubVaultConfig
					if (hubAuthService == null) {
						hubAuthService = AuthorizationService(context())
					}
					view?.showProgress(ProgressModel.GENERIC)
					unlockHubVault(unverifiedHubVaultConfig, vault)
				}
				UnlockVaultIntent.VaultAction.UNLOCK_FOR_BIOMETRIC_AUTH -> showErrorAndFinish(HubVaultOperationNotSupportedException())
				UnlockVaultIntent.VaultAction.CHANGE_PASSWORD -> showErrorAndFinish(HubVaultOperationNotSupportedException())
				UnlockVaultIntent.VaultAction.ENCRYPT_PASSWORD -> showErrorAndFinish(HubVaultOperationNotSupportedException())
			}
		}
	}

	private fun showErrorAndFinish(e: Throwable) {
		showError(e)
		finishWithResult(null)
	}

	private fun unlockHubVault(unverifiedVaultConfig: UnverifiedHubVaultConfig, vault: Vault) {
		val authIntent = buildHubAuthIntent(unverifiedVaultConfig)
		requestActivityResult(ActivityResultCallbacks.hubAuthenticationUnlock(vault, unverifiedVaultConfig), authIntent)
	}

	private fun buildHubAuthIntent(unverifiedVaultConfig: UnverifiedHubVaultConfig): Intent? {
		val serviceConfig = AuthorizationServiceConfiguration(Uri.parse(unverifiedVaultConfig.authEndpoint.toString()), Uri.parse(unverifiedVaultConfig.tokenEndpoint.toString()))
		val authRequestBuilder = AuthorizationRequest.Builder(
			serviceConfig,
			unverifiedVaultConfig.clientId,
			ResponseTypeValues.CODE,
			Uri.parse(CryptoConstants.HUB_REDIRECT_URL)
		)
		return hubAuthService?.getAuthorizationRequestIntent(authRequestBuilder.build())
	}

	@Callback(dispatchResultOkOnly = false)
	fun hubAuthenticationUnlock(result: ActivityResult, vault: Vault, unverifiedHubVaultConfig: UnverifiedHubVaultConfig) {
		if (result.isResultOk) {
			val resp = AuthorizationResponse.fromIntent(result.intent())
			if (resp != null) {
				hubAuthService?.performTokenRequest(resp.createTokenExchangeRequest()) { token, ex ->
					token?.accessToken?.let {
						unlockHubVaultUseCase
							.withVault(vault)
							.andUnverifiedVaultConfig(unverifiedHubVaultConfig)
							.andAccessToken(it)
							.run(object : DefaultResultHandler<Cloud>() {
								override fun onSuccess(cloud: Cloud) {
									finishWithResult(cloud)
								}

								override fun onError(e: Throwable) {
									when (e) {
										is HubDeviceSetupRequiredException -> view?.showCreateHubDeviceDialog(VaultModel(vault), unverifiedHubVaultConfig)
										is HubUserSetupRequiredException -> view?.showHubUserSetupRequiredDialog(unverifiedHubVaultConfig)
										is HubLicenseUpgradeRequiredException -> view?.showHubLicenseUpgradeRequiredDialog()
										is HubVaultAccessForbiddenException -> view?.showHubVaultAccessForbiddenDialog()
										is HubVaultIsArchivedException -> view?.showHubVaultIsArchivedDialog()
										else -> {
											super.onError(e)
											finishWithResult(null)
										}
									}
								}
							})
					} ?: showErrorAndFinish(HubAuthenticationFailedException(ex))
				}
			} else {
				val ex = AuthorizationException.fromIntent(result.intent())
				showErrorAndFinish(HubAuthenticationFailedException(ex))
			}
		} else {
			showErrorAndFinish(HubAuthenticationFailedException())
		}
	}

	fun onCreateHubDeviceClick(vaultModel: VaultModel, unverifiedVaultConfig: UnverifiedHubVaultConfig, deviceName: String, setupCode: String) {
		view?.showProgress(ProgressModel.GENERIC)
		val authIntent = buildHubAuthIntent(unverifiedVaultConfig)
		requestActivityResult(ActivityResultCallbacks.hubAuthenticationCreateDevice(vaultModel, unverifiedVaultConfig, deviceName, setupCode), authIntent)
	}

	@Callback(dispatchResultOkOnly = false)
	fun hubAuthenticationCreateDevice(result: ActivityResult, vault: VaultModel, unverifiedHubVaultConfig: UnverifiedHubVaultConfig, deviceName: String, setupCode: String) {
		if (result.isResultOk) {
			val resp = AuthorizationResponse.fromIntent(result.intent())
			if (resp != null) {
				hubAuthService?.performTokenRequest(resp.createTokenExchangeRequest()) { token, ex ->
					token?.accessToken?.let {
						createHubDeviceUseCase
							.withAccessToken(it)
							.andUnverifiedVaultConfig(unverifiedHubVaultConfig)
							.andDeviceName(deviceName)
							.andSetupCode(setupCode)
							.run(object : DefaultResultHandler<Void?>() {
								override fun onSuccess(void: Void?) {
									view?.showProgress(ProgressModel.COMPLETED)
									unlockHubVault(unverifiedHubVaultConfig, vault.toVault())
								}
							})
					} ?: showErrorAndFinish(HubAuthenticationFailedException(ex))
				}
			} else {
				val ex = AuthorizationException.fromIntent(result.intent())
				showErrorAndFinish(HubAuthenticationFailedException(ex))
			}
		} else {
			showErrorAndFinish(HubAuthenticationFailedException())
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
						else -> {}
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
		removeStoredVaultPasswordsAndDisableBiometricAuthUseCase.run(object : DefaultResultHandler<Void?>() {
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
									.withSavedPassword(newPassword, CryptoMode.GCM) //
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

	fun onGoToHubProfileClicked(unverifiedVaultConfig: UnverifiedHubVaultConfig) {
		val intent = Intent(Intent.ACTION_VIEW)
		intent.data = Uri.parse(unverifiedVaultConfig.apiBaseUrl.resolve("../app/profile").toString())
		requestActivityResult(ActivityResultCallbacks.onGoToHubProfileFinished(), intent)
	}

	@Callback(dispatchResultOkOnly = false)
	fun onGoToHubProfileFinished(result: ActivityResult) {
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
			unlockHubVaultUseCase, //
			prepareUnlockUseCase, //
			createHubDeviceUseCase, //
			removeStoredVaultPasswordsAndDisableBiometricAuthUseCase, //
			saveVaultUseCase
		)
	}

}
