package org.cryptomator.presentation.presenter

import android.Manifest
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.common.base.Optional
import org.cryptomator.data.cloud.crypto.CryptoCloud
import org.cryptomator.data.util.NetworkConnectionCheck
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFolder
import org.cryptomator.domain.CloudType
import org.cryptomator.domain.Vault
import org.cryptomator.domain.di.PerView
import org.cryptomator.domain.usecases.DoUpdateCheckUseCase
import org.cryptomator.domain.usecases.DoUpdateUseCase
import org.cryptomator.domain.usecases.GetDecryptedCloudForVaultUseCase
import org.cryptomator.domain.usecases.NoOpResultHandler
import org.cryptomator.domain.usecases.UpdateCheck
import org.cryptomator.domain.usecases.cloud.GetRootFolderUseCase
import org.cryptomator.domain.usecases.vault.DeleteVaultUseCase
import org.cryptomator.domain.usecases.vault.GetVaultListUseCase
import org.cryptomator.domain.usecases.vault.ListCBCEncryptedPasswordVaultsUseCase
import org.cryptomator.domain.usecases.vault.LockVaultUseCase
import org.cryptomator.domain.usecases.vault.MoveVaultPositionUseCase
import org.cryptomator.domain.usecases.vault.RemoveStoredVaultPasswordsUseCase
import org.cryptomator.domain.usecases.vault.RenameVaultUseCase
import org.cryptomator.domain.usecases.vault.SaveVaultUseCase
import org.cryptomator.domain.usecases.vault.SaveVaultsUseCase
import org.cryptomator.domain.usecases.vault.UpdateVaultParameterIfChangedRemotelyUseCase
import org.cryptomator.generator.Callback
import org.cryptomator.presentation.BuildConfig
import org.cryptomator.presentation.CryptomatorApp
import org.cryptomator.presentation.R
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.intent.Intents
import org.cryptomator.presentation.intent.UnlockVaultIntent
import org.cryptomator.presentation.licensing.LicenseEnforcer
import org.cryptomator.presentation.model.CloudModel
import org.cryptomator.presentation.model.CloudTypeModel
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.model.VaultModel
import org.cryptomator.presentation.model.mappers.CloudFolderModelMapper
import org.cryptomator.presentation.ui.activity.WelcomeActivity
import org.cryptomator.presentation.ui.activity.view.VaultListView
import org.cryptomator.presentation.ui.dialog.AppIsObscuredInfoDialog
import org.cryptomator.presentation.ui.dialog.AskForLockScreenDialog
import org.cryptomator.presentation.ui.dialog.CBCPasswordVaultsMigrationDialog
import org.cryptomator.presentation.ui.dialog.EnterPasswordDialog
import org.cryptomator.presentation.ui.dialog.TrialExpiredDialog
import org.cryptomator.presentation.ui.dialog.UpdateAppAvailableDialog
import org.cryptomator.presentation.ui.dialog.UpdateAppDialog
import org.cryptomator.presentation.ui.dialog.VaultsRemovedDuringMigrationDialog
import org.cryptomator.presentation.util.FileUtil
import org.cryptomator.presentation.workflow.ActivityResult
import org.cryptomator.presentation.workflow.AddExistingVaultWorkflow
import org.cryptomator.presentation.workflow.AuthenticationExceptionHandler
import org.cryptomator.presentation.workflow.CreateNewVaultWorkflow
import org.cryptomator.presentation.workflow.PermissionsResult
import org.cryptomator.presentation.workflow.Workflow
import org.cryptomator.util.FlavorConfig
import org.cryptomator.util.SharedPreferencesHandler
import org.cryptomator.util.crypto.CryptoMode
import javax.inject.Inject
import timber.log.Timber

@PerView
class VaultListPresenter @Inject constructor( //
	private val getVaultListUseCase: GetVaultListUseCase,  //
	private val deleteVaultUseCase: DeleteVaultUseCase,  //
	private val renameVaultUseCase: RenameVaultUseCase,  //
	private val lockVaultUseCase: LockVaultUseCase,  //
	private val getDecryptedCloudForVaultUseCase: GetDecryptedCloudForVaultUseCase,  //
	private val getRootFolderUseCase: GetRootFolderUseCase,  //
	private val addExistingVaultWorkflow: AddExistingVaultWorkflow,  //
	private val createNewVaultWorkflow: CreateNewVaultWorkflow,  //
	private val saveVaultUseCase: SaveVaultUseCase,  //
	private val moveVaultPositionUseCase: MoveVaultPositionUseCase, //
	private val updateCheckUseCase: DoUpdateCheckUseCase,  //
	private val updateUseCase: DoUpdateUseCase,  //
	private val updateVaultParameterIfChangedRemotelyUseCase: UpdateVaultParameterIfChangedRemotelyUseCase, //
	private val listCBCEncryptedPasswordVaultsUseCase: ListCBCEncryptedPasswordVaultsUseCase, //
	private val removeStoredVaultPasswordsUseCase: RemoveStoredVaultPasswordsUseCase, //
	private val saveVaultsUseCase: SaveVaultsUseCase, //
	private val networkConnectionCheck: NetworkConnectionCheck,  //
	private val fileUtil: FileUtil,  //
	private val authenticationExceptionHandler: AuthenticationExceptionHandler,  //
	private val cloudFolderModelMapper: CloudFolderModelMapper,  //
	private val licenseEnforcer: LicenseEnforcer,  //
	private val sharedPreferencesHandler: SharedPreferencesHandler,  //
	exceptionMappings: ExceptionHandlers
) : Presenter<VaultListView>(exceptionMappings) {

	private var vaultAction: VaultAction? = null
	private var hasShownTrialExpiredDialog = false

	/**
	 * Provides the presenter workflows used to add an existing vault or create a new vault.
	 *
	 * @return An iterable containing the workflows for adding an existing vault and creating a new vault.
	 */
	override fun workflows(): Iterable<Workflow<*>> {
		return listOf(addExistingVaultWorkflow, createNewVaultWorkflow)
	}

	/**
	 * Initiates the welcome flow when it hasn't been completed and, for freemium builds,
	 * shows the trial-expired dialog once if the trial is expired and no paid license exists.
	 *
	 * If the welcome flow is launched, this method returns immediately without performing
	 * further checks. For freemium flavors the trial-expired dialog is displayed at most
	 * once per presenter instance.
	 */
	override fun resumed() {
		if (launchWelcomeFlowIfNeeded()) {
			return
		}

		if (!hasShownTrialExpiredDialog && FlavorConfig.isFreemiumFlavor) {
			val trialState = licenseEnforcer.evaluateTrialState()
			if (trialState.isExpired && !licenseEnforcer.hasPaidLicense()) {
				hasShownTrialExpiredDialog = true
				view?.showDialog(TrialExpiredDialog.newInstance())
			}
		}
	}

	/**
	 * Starts the welcome flow when it has not been completed yet.
	 *
	 * If the welcome flow is incomplete, launches WelcomeActivity (with an activity-result callback).
	 *
	 * @return `true` if the welcome flow was launched, `false` otherwise.
	 */
	private fun launchWelcomeFlowIfNeeded(): Boolean {
		if (!sharedPreferencesHandler.hasCompletedWelcomeFlow()) {
			requestActivityResult(
				ActivityResultCallbacks.welcomeFlowCompleted(),
				Intent(context(), WelcomeActivity::class.java)
			)
			return true
		}
		return false
	}

	/**
	 * Reloads the vault list when the window gains focus.
	 *
	 * @param hasFocus `true` if the window now has focus, `false` otherwise.
	 */
	fun onWindowFocusChanged(hasFocus: Boolean) {
		if (hasFocus) {
			loadVaultList()
		}
	}

	/**
	 * Prepares the view by performing initial one-time checks and showing any required setup dialogs.
	 *
	 * Performs the welcome-flow gate, shows a lock-screen prompt if the device has no secure keyguard (once),
	 * displays a migration notice for vaults removed during migration, triggers an app update check for APK-store builds
	 * when configured, and initiates permission checks.
	 */
	fun prepareView() {
		if (launchWelcomeFlowIfNeeded()) {
			return
		}

		if (!sharedPreferencesHandler.isScreenLockDialogAlreadyShown) {
			val keyguardManager = context().getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
			if (!keyguardManager.isKeyguardSecure) {
				view?.showDialog(AskForLockScreenDialog.newInstance())
			}
			sharedPreferencesHandler.setScreenLockDialogAlreadyShown()
		}

		sharedPreferencesHandler.vaultsRemovedDuringMigration()?.let {
			val cloudNameString = getString(CloudTypeModel.valueOf(CloudType.valueOf(it.first)).displayNameResource)
			view?.showDialog(VaultsRemovedDuringMigrationDialog.newInstance(Pair(cloudNameString, it.second)))
			sharedPreferencesHandler.vaultsRemovedDuringMigration(null)
		}

		if (FlavorConfig.isApkStoreFlavor && sharedPreferencesHandler.doUpdate()) {
			checkForAppUpdates()
		}

		checkPermissions()
	}

	/**
	 * Initiates an application update availability check when an internet connection is present.
	 *
	 * If an update is found, delegates handling to `updateStatusRetrieved`; if no update is available,
	 * logs that the current version is up to date. Always records that an update check was executed.
	 * Any errors during the check are reported via `showError`. If no network is available, logs that
	 * the check was not started.
	 */
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
						showError(e)
					}
				})
		} else {
			Timber.tag("VaultListPresenter").i("Update check not started due to no internet connection")
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


	private fun checkPermissions() {
		if (sharedPreferencesHandler.usePhotoUpload()) {
			checkLocalStoragePermissionRegardingAutoUploadAndNotificationPermission()
		} else {
			checkNotificationPermission()
		}
	}

	private fun checkLocalStoragePermissionRegardingAutoUploadAndNotificationPermission() {
		val permissions = if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2) {
			arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
		} else {
			arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
		}
		requestPermissions(
			PermissionsResultCallbacks.onLocalStoragePermissionResultForAutoUploadAndCheckNotificationPermission(),  //
			R.string.permission_snackbar_auth_auto_upload,  //
			*permissions
		)
	}

	/**
	 * Handles the result of a local-storage permission request for auto-upload and then proceeds to notification-permission handling.
	 *
	 * Logs an error if local storage permission was not granted; in all cases invokes checkNotificationPermission().
	 *
	 * @param result The permission request result; `result.granted()` indicates the local storage permission was granted.
	 */
	@Callback
	fun onLocalStoragePermissionResultForAutoUploadAndCheckNotificationPermission(result: PermissionsResult) {
		if (!result.granted()) {
			Timber.tag("VaultListPresenter").e("Local storage permission not granted, auto upload will not work")
		}
		checkNotificationPermission()
	}

	/**
	 * Ensures the app has notification permission or advances the permission flow.
	 *
	 * If the runtime notification permission should be requested, initiates a permission request.
	 * Otherwise continues by checking for CBC-encrypted vaults.
	 */
	private fun checkNotificationPermission() {
		if (shouldRequestNotificationPermission()) {
			requestPermissions(
				PermissionsResultCallbacks.requestNotificationPermission(),  //
				R.string.permission_snackbar_notifications,  //
				Manifest.permission.POST_NOTIFICATIONS
			)
		} else {
			checkCBCEncryptedVaults()
		}
	}

	/**
	 * Determines whether the app should request the `POST_NOTIFICATIONS` runtime permission.
	 *
	 * @return `true` if the device is running a version newer than Android S_V2 and `POST_NOTIFICATIONS`
	 *         is not granted, `false` otherwise.
	 */
	private fun shouldRequestNotificationPermission(): Boolean {
		return Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2 &&
				ContextCompat.checkSelfPermission(context(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
	}

	/**
	 * Handles the result of a notification permission request and continues with CBC-encrypted vault checks.
	 *
	 * Logs an error if the notification permission was not granted and then invokes the CBC-encrypted vaults check.
	 *
	 * @param result The outcome of the permission request indicating whether the permission was granted.
	 */
	@Callback
	fun requestNotificationPermission(result: PermissionsResult) {
		if (!result.granted()) {
			Timber.tag("VaultListPresenter").e("Notification permission not granted, notifications will not show")
		}
		checkCBCEncryptedVaults()
	}

	private fun checkCBCEncryptedVaults() {
		listCBCEncryptedPasswordVaultsUseCase
			.run(object : DefaultResultHandler<List<Vault>>() {
				override fun onSuccess(vaults: List<Vault>) {
					if (vaults.isNotEmpty()) {
						view?.showDialog(CBCPasswordVaultsMigrationDialog.newInstance(vaults))
					}
				}
			})
	}

	fun cBCPasswordVaultsMigrationClicked(cbcVaults: List<Vault>) {
		val vaultModels = cbcVaults.mapTo(ArrayList()) { VaultModel(it) }
		view?.migrateCBCEncryptedPasswordVaults(vaultModels)
	}

	fun cBCPasswordVaultsMigrationRejected(cbcVaults: List<Vault>) {
		removeStoredVaultPasswordsUseCase
			.withVaults(cbcVaults)
			.run(object : DefaultResultHandler<Void?>() {
				override fun onSuccess(ignore: Void?) {
					loadVaultList()
				}
			})
	}

	fun biometricAuthenticationMigrationFinished(vaultModels: List<VaultModel>) {
		val vaults = vaultModels.map { vaultModel -> vaultModel.toVault() }
		saveVaultsUseCase //
			.withVaults(vaults) //
			.run(object : NoOpResultHandler<List<Vault>>() {
				override fun onSuccess(migratedVaults: List<Vault>) {
					loadVaultList()
				}

				override fun onError(e: Throwable) {
					showError(e)
				}
			})
	}


	fun biometricKeyInvalidated(cbcVaults: List<VaultModel>) {
		val vaults = cbcVaults.map { vaultModel -> vaultModel.toVault() }
		removeStoredVaultPasswordsUseCase
			.withVaults(vaults)
			.run(object : DefaultResultHandler<Void?>() {
				override fun onSuccess(ignore: Void?) {
					loadVaultList()
				}
			})
	}

	fun biometricAuthenticationFailed(cbcVaults: List<VaultModel>) {
		val vaults = cbcVaults.map { vaultModel -> vaultModel.toVault() }
		view?.showDialog(CBCPasswordVaultsMigrationDialog.newInstance(vaults))
	}

	fun loadVaultList() {
		view?.hideVaultCreationHint()
		vaultList
		assertUnlockingVaultIsLocked()
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

	fun deleteVault(vaultModel: VaultModel) {
		deleteVaultUseCase //
			.withVault(vaultModel.toVault()) //
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
							ActivityResultCallbacks.renameVaultAfterAuthentication(vaultModel.toVault(), newVaultName)
						)
					) {
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
					}
					view?.renderVaultList(vaultModels)
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
		startVaultAction(vault, VaultAction.UNLOCK)
	}

	private fun startVaultAction(vault: VaultModel, vaultAction: VaultAction) {
		if (vault.passwordCryptoMode?.equals(CryptoMode.CBC) == true) {
			listCBCEncryptedPasswordVaultsUseCase
				.run(object : DefaultResultHandler<List<Vault>>() {
					override fun onSuccess(vaults: List<Vault>) {
						if (vaults.isNotEmpty()) {
							view?.showDialog(CBCPasswordVaultsMigrationDialog.newInstance(vaults))
						}
					}
				})
		} else {
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
	}

	private fun onVaultWithoutCloudClickedAndLocked(vault: VaultModel) {
		if (isWebdavOrLocal(vault.cloudType)) {
			requestActivityResult( //
				ActivityResultCallbacks.cloudConnectionForVaultSelected(vault),  //
				Intents.cloudConnectionListIntent() //
					.withCloudType(vault.cloudType) //
					.withDialogTitle(context().getString(R.string.screen_cloud_connections_title)) //
					.withFinishOnCloudItemClick(true)
			)
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
			else -> {}
		}
		vaultAction = null
	}

	/**
	 * Ensures the provided authenticated vault is presented and either starts the unlock flow when it's locked or opens its contents when unlocked.
	 *
	 * @param authenticatedVault The vault model to present; if locked, triggers the user unlock flow, otherwise navigates into the vault's contents.
	 */
	private fun requireUserAuthentication(authenticatedVault: VaultModel) {
		view?.addOrUpdateVault(authenticatedVault)
		if (authenticatedVault.isLocked) {
			if (!isPaused) {
				requestActivityResult( //
					ActivityResultCallbacks.vaultUnlockedVaultList(), //
					Intents.unlockVaultIntent().withVaultModel(authenticatedVault).withVaultAction(UnlockVaultIntent.VaultAction.UNLOCK)
				)
			}
		} else {
			browseFilesOf(authenticatedVault)
		}
	}

	/**
	 * Continues presenter setup after the welcome flow completes.
	 *
	 * @param result The ActivityResult returned by the welcome flow activity.
	 */
	@Callback
	fun welcomeFlowCompleted(result: ActivityResult) {
		prepareView()
	}

	/**
	 * Handles an activity result containing a selected Cloud and initiates loading of its root folder.
	 *
	 * @param result The ActivityResult whose intent carries the selected Cloud under the `SINGLE_RESULT` extra.
	 */
	@Callback
	fun vaultUnlockedVaultList(result: ActivityResult) {
		val cloud = result.intent().getSerializableExtra(SINGLE_RESULT) as Cloud
		getRootFolderOf(cloud)
	}

	private fun getRootFolderOf(cloud: Cloud) {
		getRootFolderUseCase //
			.withCloud(cloud) //
			.run(object : DefaultResultHandler<CloudFolder>() {
				override fun onSuccess(folder: CloudFolder) {
					navigateToVaultContent(folder)
				}
			})
	}

	private fun navigateToVaultContent(folder: CloudFolder) {
		val cryptoCloud = (folder.cloud as CryptoCloud)
		updateVaultParameterIfChangedRemotelyUseCase //
			.withVault(cryptoCloud.vault) //
			.run(object : DefaultResultHandler<Vault>() {
				override fun onSuccess(vault: Vault) {
					view?.addOrUpdateVault(VaultModel(vault))
					navigateToVaultContent(vault, folder)
					view?.showProgress(ProgressModel.COMPLETED)
					if (checkToStartAutoImageUpload(vault)) {
						val cryptomatorApp = activity().application as CryptomatorApp
						cryptomatorApp.startAutoUpload(cryptoCloud)
					}
				}
			})
	}

	private fun checkToStartAutoImageUpload(vault: Vault): Boolean {
		return if (sharedPreferencesHandler.usePhotoUpload() && sharedPreferencesHandler.photoUploadVault() == vault.id) {
			!sharedPreferencesHandler.autoPhotoUploadOnlyUsingWifi() || networkConnectionCheck.checkWifiOnAndConnected()
		} else false
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
		Intents
			.unlockVaultIntent()
			.withVaultModel(vaultModel)
			.withVaultAction(UnlockVaultIntent.VaultAction.CHANGE_PASSWORD)
			.startActivity(this)
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

	private enum class VaultAction {
		UNLOCK, RENAME
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

	init {
		unsubscribeOnDestroy( //
			deleteVaultUseCase,  //
			renameVaultUseCase,  //
			lockVaultUseCase,  //
			getVaultListUseCase,  //
			saveVaultUseCase,  //
			moveVaultPositionUseCase, //
			updateCheckUseCase,  //
			updateUseCase, //
			listCBCEncryptedPasswordVaultsUseCase, //
			removeStoredVaultPasswordsUseCase, //
			saveVaultsUseCase, //
			updateVaultParameterIfChangedRemotelyUseCase
		)
	}
}
