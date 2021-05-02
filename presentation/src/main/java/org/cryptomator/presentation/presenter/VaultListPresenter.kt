package org.cryptomator.presentation.presenter

import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.widget.Toast
import androidx.biometric.BiometricManager
import org.cryptomator.data.cloud.crypto.CryptoCloud
import org.cryptomator.data.util.NetworkConnectionCheck
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFolder
import org.cryptomator.domain.Vault
import org.cryptomator.domain.di.PerView
import org.cryptomator.domain.exception.NetworkConnectionException
import org.cryptomator.domain.exception.authentication.AuthenticationException
import org.cryptomator.domain.exception.license.LicenseNotValidException
import org.cryptomator.domain.exception.update.SSLHandshakePreAndroid5UpdateCheckException
import org.cryptomator.domain.usecases.DoLicenseCheckUseCase
import org.cryptomator.domain.usecases.DoUpdateCheckUseCase
import org.cryptomator.domain.usecases.DoUpdateUseCase
import org.cryptomator.domain.usecases.GetDecryptedCloudForVaultUseCase
import org.cryptomator.domain.usecases.LicenseCheck
import org.cryptomator.domain.usecases.NoOpResultHandler
import org.cryptomator.domain.usecases.UpdateCheck
import org.cryptomator.domain.usecases.cloud.GetRootFolderUseCase
import org.cryptomator.domain.usecases.vault.ChangePasswordUseCase
import org.cryptomator.domain.usecases.vault.DeleteVaultUseCase
import org.cryptomator.domain.usecases.vault.GetVaultListUseCase
import org.cryptomator.domain.usecases.vault.LockVaultUseCase
import org.cryptomator.domain.usecases.vault.MoveVaultPositionUseCase
import org.cryptomator.domain.usecases.vault.PrepareUnlockUseCase
import org.cryptomator.domain.usecases.vault.RemoveStoredVaultPasswordsUseCase
import org.cryptomator.domain.usecases.vault.RenameVaultUseCase
import org.cryptomator.domain.usecases.vault.SaveVaultUseCase
import org.cryptomator.domain.usecases.vault.UnlockToken
import org.cryptomator.domain.usecases.vault.UnlockVaultUseCase
import org.cryptomator.domain.usecases.vault.VaultOrUnlockToken
import org.cryptomator.generator.Callback
import org.cryptomator.presentation.BuildConfig
import org.cryptomator.presentation.R
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.intent.Intents
import org.cryptomator.presentation.model.CloudModel
import org.cryptomator.presentation.model.CloudTypeModel
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.model.ProgressStateModel
import org.cryptomator.presentation.model.VaultModel
import org.cryptomator.presentation.model.mappers.CloudFolderModelMapper
import org.cryptomator.presentation.service.AutoUploadService
import org.cryptomator.presentation.ui.activity.LicenseCheckActivity
import org.cryptomator.presentation.ui.activity.view.VaultListView
import org.cryptomator.presentation.ui.dialog.AppIsObscuredInfoDialog
import org.cryptomator.presentation.ui.dialog.AskForLockScreenDialog
import org.cryptomator.presentation.ui.dialog.EnterPasswordDialog
import org.cryptomator.presentation.ui.dialog.UpdateAppAvailableDialog
import org.cryptomator.presentation.ui.dialog.UpdateAppDialog
import org.cryptomator.presentation.util.FileUtil
import org.cryptomator.presentation.workflow.ActivityResult
import org.cryptomator.presentation.workflow.AddExistingVaultWorkflow
import org.cryptomator.presentation.workflow.AuthenticationExceptionHandler
import org.cryptomator.presentation.workflow.CreateNewVaultWorkflow
import org.cryptomator.presentation.workflow.Workflow
import org.cryptomator.util.Optional
import org.cryptomator.util.SharedPreferencesHandler
import java.io.Serializable
import javax.inject.Inject
import timber.log.Timber

@PerView
class VaultListPresenter @Inject constructor( //
		private val getVaultListUseCase: GetVaultListUseCase,  //
		private val deleteVaultUseCase: DeleteVaultUseCase,  //
		private val renameVaultUseCase: RenameVaultUseCase,  //
		private val lockVaultUseCase: LockVaultUseCase,  //
		private val getDecryptedCloudForVaultUseCase: GetDecryptedCloudForVaultUseCase,  //
		private val prepareUnlockUseCase: PrepareUnlockUseCase,  //
		private val unlockVaultUseCase: UnlockVaultUseCase,  //
		private val getRootFolderUseCase: GetRootFolderUseCase,  //
		private val addExistingVaultWorkflow: AddExistingVaultWorkflow,  //
		private val createNewVaultWorkflow: CreateNewVaultWorkflow,  //
		private val saveVaultUseCase: SaveVaultUseCase,  //
		private val moveVaultPositionUseCase: MoveVaultPositionUseCase, //
		private val changePasswordUseCase: ChangePasswordUseCase,  //
		private val removeStoredVaultPasswordsUseCase: RemoveStoredVaultPasswordsUseCase,  //
		private val licenseCheckUseCase: DoLicenseCheckUseCase,  //
		private val updateCheckUseCase: DoUpdateCheckUseCase,  //
		private val updateUseCase: DoUpdateUseCase,  //
		private val networkConnectionCheck: NetworkConnectionCheck,  //
		private val fileUtil: FileUtil,  //
		private val authenticationExceptionHandler: AuthenticationExceptionHandler,  //
		private val cloudFolderModelMapper: CloudFolderModelMapper,  //
		private val sharedPreferencesHandler: SharedPreferencesHandler,  //
		exceptionMappings: ExceptionHandlers) : Presenter<VaultListView>(exceptionMappings) {

	private var vaultAction: VaultAction? = null
	private var changedVaultPassword = false
	private var startedUsingPrepareUnlock = false
	private var retryUnlockHandler: Handler? = null

	@Volatile
	private var running = false
	override fun workflows(): Iterable<Workflow<*>> {
		return listOf(addExistingVaultWorkflow, createNewVaultWorkflow)
	}

	override fun destroyed() {
		super.destroyed()
		if (retryUnlockHandler != null) {
			running = false
			retryUnlockHandler?.removeCallbacks(null)
		}
	}

	fun onWindowFocusChanged(hasFocus: Boolean) {
		if (hasFocus) {
			loadVaultList()
			if (retryUnlockHandler != null) {
				running = false
				retryUnlockHandler?.removeCallbacks(null)
			}
		}
	}

	fun prepareView() {
		if (!sharedPreferencesHandler.isScreenLockDialogAlreadyShown) {
			val keyguardManager = context().getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
			if (!keyguardManager.isKeyguardSecure) {
				view?.showDialog(AskForLockScreenDialog.newInstance())
			}
			sharedPreferencesHandler.setScreenLockDialogAlreadyShown()
		}
		checkLicense()
	}

	private fun checkLicense() {
		if (BuildConfig.FLAVOR == "apkstore" || BuildConfig.FLAVOR == "fdroid") {
			licenseCheckUseCase //
					.withLicense("") //
					.run(object : NoOpResultHandler<LicenseCheck>() {
						override fun onSuccess(licenseCheck: LicenseCheck) {
							if (BuildConfig.FLAVOR == "apkstore" && sharedPreferencesHandler.doUpdate()) {
								checkForAppUpdates()
							}
						}

						override fun onError(e: Throwable) {
							var license: String? = ""
							if (e is LicenseNotValidException) {
								license = e.license
							}
							val intent = Intent(context(), LicenseCheckActivity::class.java)
							intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
							intent.data = Uri.parse(String.format("app://cryptomator/%s", license))

							try {
								context().startActivity(intent)
							} catch (e: ActivityNotFoundException) {
								Toast.makeText(context(), "Please contact the support.", Toast.LENGTH_LONG).show()
								finish()
							}
						}
					})
		}
	}

	private fun checkForAppUpdates() {
		if (networkConnectionCheck.isPresent) {
			updateCheckUseCase //
					.withVersion(BuildConfig.VERSION_NAME) //
					.run(object : NoOpResultHandler<Optional<UpdateCheck>>() {
						override fun onSuccess(updateCheck: Optional<UpdateCheck>) {
							if (updateCheck.isPresent) {
								updateStatusRetrieved(updateCheck.get(), context())
							} else {
								Timber.tag("VaultListPresenter").i("UpdateCheck finished, latest version")
							}
							sharedPreferencesHandler.updateExecuted()
						}

						override fun onError(e: Throwable) {
							if (e is SSLHandshakePreAndroid5UpdateCheckException) {
								Timber.tag("SettingsPresenter").e(e, "Update check failed due to Android pre 5 and SSL Handshake not accepted")
							} else {
								showError(e)
							}
						}
					})
		} else {
			Timber.tag("VaultListPresenter").i("Update check not started due to no internal connection")
		}
	}

	private fun updateStatusRetrieved(updateCheck: UpdateCheck, context: Context) {
		showNextMessage(updateCheck.releaseNote(), context)
	}

	private fun showNextMessage(message: String, context: Context) {
		if (message.isNotEmpty()) {
			view?.showDialog(UpdateAppAvailableDialog.newInstance(message))
		} else {
			view?.showDialog(UpdateAppAvailableDialog.newInstance(context.getText(R.string.dialog_update_available_message).toString()))
		}
	}

	private fun assertUnlockingVaultIsLocked() {
		if (view?.isShowingDialog(EnterPasswordDialog::class) == true) {
			if (view?.currentDialog() != null) {
				val vaultModel = (view?.currentDialog() as EnterPasswordDialog).vaultModel()
				if (view?.isVaultLocked(vaultModel) == false) {
					view?.closeDialog()
				}
			}
		}
	}

	fun loadVaultList() {
		view?.hideVaultCreationHint()
		vaultList
		assertUnlockingVaultIsLocked()
	}

	fun deleteVault(vaultModel: VaultModel) {
		deleteVault(vaultModel.toVault())
	}

	private fun deleteVault(vault: Vault) {
		deleteVaultUseCase //
				.withVault(vault) //
				.run(object : DefaultResultHandler<Long>() {
					override fun onSuccess(vaultId: Long) {
						view?.deleteVaultFromAdapter(vaultId)
					}
				})
	}

	fun renameVault(vaultModel: VaultModel, newVaultName: String?) {
		renameVaultUseCase //
				.withVault(vaultModel.toVault()) //
				.andNewVaultName(newVaultName) //
				.run(object : DefaultResultHandler<Vault>() {
					override fun onSuccess(vault: Vault) {
						view?.renameVault(VaultModel(vault))
						view?.closeDialog()
					}

					override fun onError(e: Throwable) {
						if (!authenticationExceptionHandler.handleAuthenticationException( //
										this@VaultListPresenter, e,  //
										ActivityResultCallbacks.renameVaultAfterAuthentication(vaultModel.toVault(), newVaultName))) {
							showError(e)
						}
					}
				})
	}

	@Callback
	fun renameVaultAfterAuthentication(result: ActivityResult, vault: Vault?, newVaultName: String?) {
		val cloud = result.getSingleResult(CloudModel::class.java).toCloud()
		val vaultWithUpdatedCloud = Vault.aCopyOf(vault).withCloud(cloud).build()
		renameVault(VaultModel(vaultWithUpdatedCloud), newVaultName)
	}

	fun onUnlockCanceled() {
		prepareUnlockUseCase.unsubscribe()
		unlockVaultUseCase.cancel()
	}

	private fun browseFilesOf(vault: VaultModel) {
		getDecryptedCloudForVaultUseCase //
				.withVault(vault.toVault()) //
				.run(object : DefaultResultHandler<Cloud>() {
					override fun onSuccess(cloud: Cloud) {
						getRootFolderAndNavigateInto(cloud)
					}
				})
	}

	private fun getRootFolderAndNavigateInto(cloud: Cloud) {
		getRootFolderUseCase //
				.withCloud(cloud) //
				.run(object : DefaultResultHandler<CloudFolder>() {
					override fun onSuccess(folder: CloudFolder) {
						navigateToVaultContent((folder.cloud as CryptoCloud).vault, folder)
					}
				})
	}

	private fun lockVault(vaultModel: VaultModel) {
		lockVaultUseCase //
				.withVault(vaultModel.toVault()) //
				.run(object : DefaultResultHandler<Vault>() {
					override fun onSuccess(vault: Vault) {
						view?.addOrUpdateVault(VaultModel(vault))
					}
				})
	}

	private val vaultList: Unit
		get() {
			getVaultListUseCase.run(object : DefaultResultHandler<List<Vault>>() {
				override fun onSuccess(vaults: List<Vault>) {
					val vaultModels = vaults.mapTo(ArrayList()) { VaultModel(it) }
					if (vaultModels.isEmpty()) {
						view?.showVaultCreationHint()
					} else {
						view?.hideVaultCreationHint()
						view?.renderVaultList(vaultModels)
					}
				}
			})
		}

	private fun navigateToVaultContent(vault: Vault, cloudFolder: CloudFolder) {
		if (!isPaused) {
			view?.navigateToVaultContent(VaultModel(vault), cloudFolderModelMapper.toModel(cloudFolder))
		}
	}

	fun onVaultLockClicked(vault: VaultModel) {
		lockVault(vault)
	}

	fun onVaultClicked(vault: VaultModel) {
		startedUsingPrepareUnlock = sharedPreferencesHandler.backgroundUnlockPreparation()
		startVaultAction(vault, VaultAction.UNLOCK)
	}

	private fun startVaultAction(vault: VaultModel, vaultAction: VaultAction) {
		this.vaultAction = vaultAction
		val cloud = vault.toVault().cloud
		if (cloud != null) {
			onCloudOfVaultAuthenticated(vault.toVault())
		} else {
			if (vault.isLocked) {
				onVaultWithoutCloudClickedAndLocked(vault)
			} else {
				lockVaultUseCase //
						.withVault(vault.toVault()) //
						.run(object : DefaultResultHandler<Vault>() {
							override fun onSuccess(vault: Vault) {
								onVaultWithoutCloudClickedAndLocked(VaultModel(vault))
							}
						})
			}
		}
	}

	private fun onVaultWithoutCloudClickedAndLocked(vault: VaultModel) {
		if (isWebdavOrLocal(vault.cloudType)) {
			requestActivityResult( //
					ActivityResultCallbacks.cloudConnectionForVaultSelected(vault),  //
					Intents.cloudConnectionListIntent() //
							.withCloudType(vault.cloudType) //
							.withDialogTitle(context().getString(R.string.screen_cloud_connections_title)) //
							.withFinishOnCloudItemClick(true))
		}
	}

	private fun isWebdavOrLocal(cloudType: CloudTypeModel): Boolean {
		return cloudType == CloudTypeModel.WEBDAV || cloudType == CloudTypeModel.LOCAL
	}

	@Callback
	fun cloudConnectionForVaultSelected(result: ActivityResult, vaultModel: VaultModel) {
		val cloud = result.intent().getSerializableExtra(CloudConnectionListPresenter.SELECTED_CLOUD) as Cloud
		val vault = Vault.aCopyOf(vaultModel.toVault()) //
				.withCloud(cloud) //
				.build()
		saveVaultUseCase //
				.withVault(vault) //
				.run(object : DefaultResultHandler<Vault>() {
					override fun onSuccess(vault: Vault) {
						view?.addOrUpdateVault(VaultModel(vault))
						onCloudOfVaultAuthenticated(vault)
					}
				})
	}

	private fun onCloudOfVaultAuthenticated(authenticatedVault: Vault) {
		val authenticatedVaultModel = VaultModel(authenticatedVault)
		when (vaultAction) {
			VaultAction.UNLOCK -> requireUserAuthentication(authenticatedVaultModel)
			VaultAction.RENAME -> view?.showRenameDialog(authenticatedVaultModel)
			VaultAction.CHANGE_PASSWORD -> view?.showChangePasswordDialog(authenticatedVaultModel)
		}
		vaultAction = null
	}

	private fun requireUserAuthentication(authenticatedVault: VaultModel) {
		view?.addOrUpdateVault(authenticatedVault)
		if (authenticatedVault.isLocked) {
			if (!isPaused) {
				if (canUseBiometricOn(authenticatedVault)) {
					if (startedUsingPrepareUnlock) {
						startPrepareUnlockUseCase(authenticatedVault.toVault())
					}
					view?.showBiometricDialog(authenticatedVault)
				} else {
					startPrepareUnlockUseCase(authenticatedVault.toVault())
					view?.showEnterPasswordDialog(authenticatedVault)
				}
			}
		} else {
			browseFilesOf(authenticatedVault)
		}
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
						if (!authenticationExceptionHandler.handleAuthenticationException(this@VaultListPresenter, e, ActivityResultCallbacks.authenticatedAfterUnlock(vault))) {
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

	private fun doUnlock(token: UnlockToken, password: String) {
		unlockVaultUseCase //
				.withVaultOrUnlockToken(VaultOrUnlockToken.from(token)) //
				.andPassword(password) //
				.run(object : DefaultResultHandler<Cloud>() {
					override fun onSuccess(cloud: Cloud) {
						navigateToVaultContent(cloud)
					}
				})
	}

	private fun navigateToVaultContent(cloud: Cloud) {
		getRootFolderUseCase //
				.withCloud(cloud) //
				.run(object : DefaultResultHandler<CloudFolder>() {
					override fun onSuccess(folder: CloudFolder) {
						val vault = (folder.cloud as CryptoCloud).vault
						view?.addOrUpdateVault(VaultModel(vault))
						navigateToVaultContent(vault, folder)
						view?.showProgress(ProgressModel.COMPLETED)
						if (checkToStartAutoImageUpload(vault)) {
							context().startService(AutoUploadService.startAutoUploadIntent(context(), folder.cloud))
						}
					}
				})
	}

	private fun checkToStartAutoImageUpload(vault: Vault): Boolean {
		return if (sharedPreferencesHandler.usePhotoUpload() && sharedPreferencesHandler.photoUploadVault() == vault.id) {
			!sharedPreferencesHandler.autoPhotoUploadOnlyUsingWifi() || networkConnectionCheck.checkWifiOnAndConnected()
		} else false
	}

	private fun unlockTokenObtained(unlockToken: UnlockToken) {
		pendingUnlockFor(unlockToken.vault)?.setUnlockToken(unlockToken, this)
	}

	fun onUnlockClick(vault: VaultModel, password: String?) {
		view?.showProgress(ProgressModel.GENERIC)
		pendingUnlockFor(vault.toVault())?.setPassword(password, this)
	}

	private var pendingUnlock: PendingUnlock? = null
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

	private fun canUseBiometricOn(vault: VaultModel): Boolean {
		return vault.password != null && BiometricManager //
				.from(context()) //
				.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
	}

	fun onAddExistingVault() {
		addExistingVaultWorkflow.start()
	}

	fun onCreateVault() {
		createNewVaultWorkflow.start()
	}

	fun onAddOrCreateVaultCompleted(vault: Vault) {
		view?.addOrUpdateVault(VaultModel(vault))
		view?.hideVaultCreationHint()
		view?.closeDialog()
	}

	fun onChangePasswordClicked(vaultModel: VaultModel) {
		startVaultAction(vaultModel, VaultAction.CHANGE_PASSWORD)
	}

	fun onChangePasswordClicked(vaultModel: VaultModel, oldPassword: String?, newPassword: String?) {
		view?.showProgress(ProgressModel(ProgressStateModel.CHANGING_PASSWORD))
		changePasswordUseCase.withVault(vaultModel.toVault()) //
				.andOldPassword(oldPassword) //
				.andNewPassword(newPassword) //
				.run(object : DefaultResultHandler<Void?>() {
					override fun onSuccess(void: Void?) {
						view?.showProgress(ProgressModel.COMPLETED)
						view?.showMessage(R.string.screen_vault_list_change_password_successful)
						if (canUseBiometricOn(vaultModel)) {
							changedVaultPassword = true
							view?.getEncryptedPasswordWithBiometricAuthentication(VaultModel( //
									Vault.aCopyOf(vaultModel.toVault()) //
											.withSavedPassword(newPassword) //
											.build()))
						}
					}

					override fun onError(e: Throwable) {
						if (!authenticationExceptionHandler.handleAuthenticationException( //
										this@VaultListPresenter, e,  //
										ActivityResultCallbacks.changePasswordAfterAuthentication(vaultModel.toVault(), oldPassword, newPassword))) {
							showError(e)
						}
					}
				})
	}

	@Callback
	fun changePasswordAfterAuthentication(result: ActivityResult, vault: Vault?, oldPassword: String?, newPassword: String?) {
		val cloud = result.getSingleResult(CloudModel::class.java).toCloud()
		val vaultWithUpdatedCloud = Vault.aCopyOf(vault).withCloud(cloud).build()
		onChangePasswordClicked(VaultModel(vaultWithUpdatedCloud), oldPassword, newPassword)
	}

	private fun save(vaultModel: VaultModel) {
		saveVaultUseCase //
				.withVault(vaultModel.toVault()) //
				.run(DefaultResultHandler())
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

	fun onVaultSettingsClicked(vaultModel: VaultModel) {
		view?.showVaultSettingsDialog(vaultModel)
	}

	fun onCreateVaultClicked() {
		view?.showAddVaultBottomSheet()
	}

	fun onRenameVaultClicked(vaultModel: VaultModel) {
		startVaultAction(vaultModel, VaultAction.RENAME)
	}

	fun onAskForLockScreenFinished(setScreenLock: Boolean) {
		if (setScreenLock) {
			try {
				view?.activity()?.startActivity(Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD))
			} catch (e: ActivityNotFoundException) {
				Timber.tag("VaultListPresenter").d(e, "Device Policy Manager not found")
				view?.showError(R.string.error_device_policy_manager_not_found)
			}
		}
	}

	fun onDeleteMissingVaultClicked(vault: Vault) {
		deleteVault(vault)
	}

	fun onFilteredTouchEventForSecurity() {
		view?.showDialog(AppIsObscuredInfoDialog.newInstance())
	}

	fun onRowMoved(fromPosition: Int, toPosition: Int) {
		view?.rowMoved(fromPosition, toPosition)
	}

	fun onVaultMoved(fromPosition: Int, toPosition: Int) {
		moveVaultPositionUseCase
				.withFromPosition(fromPosition) //
				.andToPosition(toPosition) //
				.run(object : DefaultResultHandler<List<Vault>>() {
					override fun onSuccess(vaults: List<Vault>) {
						view?.vaultMoved(vaults.mapTo(ArrayList()) { VaultModel(it) })
					}

					override fun onError(e: Throwable) {
						Timber.tag("VaultListPresenter").e(e, "Failed to execute MoveVaultUseCase")
					}
				})
	}

	fun onBiometricAuthenticationSucceeded(vaultModel: VaultModel) {
		if (changedVaultPassword) {
			changedVaultPassword = false
			save(vaultModel)
		} else {
			if (startedUsingPrepareUnlock) {
				onUnlockClick(vaultModel, vaultModel.password)
			} else {
				view?.showProgress(ProgressModel.GENERIC)
				startPrepareUnlockUseCase(vaultModel.toVault())
			}
		}
	}

	fun useConfirmationInFaceUnlockBiometricAuthentication(): Boolean {
		return sharedPreferencesHandler.useConfirmationInFaceUnlockBiometricAuthentication()
	}

	private enum class VaultAction {
		UNLOCK, RENAME, CHANGE_PASSWORD
	}

	fun installUpdate() {
		view?.showDialog(UpdateAppDialog.newInstance())
		val uri = fileUtil.contentUriForNewTempFile("cryptomator.apk")
		val file = fileUtil.tempFile("cryptomator.apk")
		updateUseCase //
				.withFile(file) //
				.run(object : NoOpResultHandler<Void?>() {
					override fun onError(e: Throwable) {
						showError(e)
					}

					override fun onSuccess(aVoid: Void?) {
						super.onSuccess(aVoid)
						val intent = Intent(Intent.ACTION_VIEW)
						intent.setDataAndType(uri, "application/vnd.android.package-archive")
						intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
						intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
						context().startActivity(intent)
					}
				})
	}

	fun startedUsingPrepareUnlock(): Boolean {
		return startedUsingPrepareUnlock
	}

	private open class PendingUnlock(private val vault: Vault?) : Serializable {

		private var unlockToken: UnlockToken? = null
		private var password: String? = null

		fun setUnlockToken(unlockToken: UnlockToken?, presenter: VaultListPresenter) {
			this.unlockToken = unlockToken
			continueIfComplete(presenter)
		}

		fun setPassword(password: String?, presenter: VaultListPresenter) {
			this.password = password
			continueIfComplete(presenter)
		}

		open fun continueIfComplete(presenter: VaultListPresenter) {
			unlockToken?.let { token -> password?.let { password -> presenter.doUnlock(token, password) } }
		}

		fun belongsTo(vault: Vault): Boolean {
			return vault == this.vault
		}

		companion object {

			val NO_OP_PENDING_UNLOCK: PendingUnlock = object : PendingUnlock(null) {
				override fun continueIfComplete(presenter: VaultListPresenter) {
					// empty
				}
			}
		}
	}

	init {
		unsubscribeOnDestroy( //
				deleteVaultUseCase,  //
				renameVaultUseCase,  //
				lockVaultUseCase,  //
				getVaultListUseCase,  //
				saveVaultUseCase,  //
				moveVaultPositionUseCase, //
				removeStoredVaultPasswordsUseCase,  //
				unlockVaultUseCase,  //
				prepareUnlockUseCase,  //
				licenseCheckUseCase,  //
				updateCheckUseCase,  //
				updateUseCase)
	}
}
