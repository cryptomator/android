package org.cryptomator.presentation.presenter

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFolder
import org.cryptomator.domain.Vault
import org.cryptomator.domain.di.PerView
import org.cryptomator.domain.usecases.GetDecryptedCloudForVaultUseCase
import org.cryptomator.domain.usecases.cloud.GetRootFolderUseCase
import org.cryptomator.domain.usecases.vault.GetVaultListUseCase
import org.cryptomator.generator.Callback
import org.cryptomator.presentation.R
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.intent.ChooseCloudNodeSettings
import org.cryptomator.presentation.intent.Intents
import org.cryptomator.presentation.intent.UnlockVaultIntent
import org.cryptomator.presentation.model.CloudFolderModel
import org.cryptomator.presentation.model.CloudModel
import org.cryptomator.presentation.model.VaultModel
import org.cryptomator.presentation.model.mappers.CloudFolderModelMapper
import org.cryptomator.presentation.ui.activity.view.AutoUploadChooseVaultView
import org.cryptomator.presentation.workflow.ActivityResult
import org.cryptomator.presentation.workflow.AuthenticationExceptionHandler
import org.cryptomator.util.SharedPreferencesHandler
import java.util.*
import javax.inject.Inject

@PerView
class AutoUploadChooseVaultPresenter @Inject constructor( //
	private val getVaultListUseCase: GetVaultListUseCase,  //
	private val getRootFolderUseCase: GetRootFolderUseCase,  //
	private val getDecryptedCloudForVaultUseCase: GetDecryptedCloudForVaultUseCase,  //
	private val cloudFolderModelMapper: CloudFolderModelMapper,  //
	private val sharedPreferencesHandler: SharedPreferencesHandler,  //
	private val authenticationExceptionHandler: AuthenticationExceptionHandler,  //
	exceptionMappings: ExceptionHandlers
) : Presenter<AutoUploadChooseVaultView>(exceptionMappings) {

	private var selectedVault: VaultModel? = null
	private var location: CloudFolderModel? = null
	private var authenticationState: AuthenticationState? = null

	fun displayVaults() {
		getVaultListUseCase.run(object : DefaultResultHandler<List<Vault>>() {
			override fun onSuccess(vaults: List<Vault>) {
				if (vaults.isEmpty()) {
					view?.displayDialogUnableToUploadFiles()
				} else {
					val vaultModels = vaults.mapTo(ArrayList()) { VaultModel(it) }
					view?.displayVaults(vaultModels)
				}
			}
		})
	}

	fun onVaultSelected(vault: VaultModel?) {
		selectedVault = vault
	}

	fun onChooseVaultPressed() {
		selectedVault?.let { sharedPreferencesHandler.photoUploadVault(it.vaultId) }
		finish()
	}

	fun onChooseLocationPressed() {
		authenticate(selectedVault)
	}

	private fun authenticate(vaultModel: VaultModel?, authenticationState: AuthenticationState = AuthenticationState.CHOOSE_LOCATION) {
		setAuthenticationState(authenticationState)
		vaultModel?.let { onCloudOfVaultAuthenticated(it.toVault()) }
	}

	private fun setAuthenticationState(authenticationState: AuthenticationState) {
		this.authenticationState = authenticationState
	}

	private fun onCloudOfVaultAuthenticated(authenticatedVault: Vault) {
		if (authenticatedVault.isUnlocked) {
			decryptedCloudFor(authenticatedVault)
		} else {
			if (!isPaused) {
				requestActivityResult( //
					ActivityResultCallbacks.vaultUnlockedAutoUpload(), //
					Intents.unlockVaultIntent().withVaultModel(VaultModel(authenticatedVault)).withVaultAction(UnlockVaultIntent.VaultAction.UNLOCK)
				)
			}
		}
	}

	@Callback
	fun vaultUnlockedAutoUpload(result: ActivityResult) {
		val cloud = result.intent().getSerializableExtra(SINGLE_RESULT) as Cloud
		rootFolderFor(cloud)
	}


	private fun decryptedCloudFor(vault: Vault) {
		getDecryptedCloudForVaultUseCase //
			.withVault(vault) //
			.run(object : DefaultResultHandler<Cloud>() {
				override fun onSuccess(cloud: Cloud) {
					rootFolderFor(cloud)
				}

				override fun onError(e: Throwable) {
					if (!authenticationExceptionHandler.handleAuthenticationException( //
							this@AutoUploadChooseVaultPresenter,  //
							e,  //
							ActivityResultCallbacks.decryptedCloudForAfterAuthInAutoPhotoUpload(vault)
						)
					) {
						super.onError(e)
					}
				}
			})
	}

	@Callback
	fun decryptedCloudForAfterAuthInAutoPhotoUpload(result: ActivityResult, vault: Vault?) {
		val cloud = result.getSingleResult(CloudModel::class.java).toCloud()
		decryptedCloudFor(Vault.aCopyOf(vault).withCloud(cloud).build())
	}

	private fun rootFolderFor(cloud: Cloud) {
		getRootFolderUseCase //
			.withCloud(cloud) //
			.run(object : DefaultResultHandler<CloudFolder>() {
				override fun onSuccess(folder: CloudFolder) {
					when (authenticationState) {
						AuthenticationState.CHOOSE_LOCATION -> {
							location = cloudFolderModelMapper.toModel(folder)
							selectedVault?.let { navigateToVaultContent(it, location) }
						}
						AuthenticationState.INIT_ROOT -> location = cloudFolderModelMapper.toModel(folder)
					}
				}
			})
	}

	private fun navigateToVaultContent(vaultModel: VaultModel, decryptedRoot: CloudFolderModel?) {
		requestActivityResult( //
			ActivityResultCallbacks.onAutoUploadChooseLocation(vaultModel),  //
			Intents.browseFilesIntent() //
				.withFolder(decryptedRoot) //
				.withTitle(vaultModel.name) //
				.withChooseCloudNodeSettings( //
					ChooseCloudNodeSettings.chooseCloudNodeSettings() //
						.withExtraTitle(context().getString(R.string.screen_file_browser_share_destination_title)) //
						.withExtraToolbarIcon(R.drawable.ic_clear) //
						.withButtonText(context().getString(R.string.screen_file_browser_share_button_text)) //
						.selectingFolders() //
						.build()
				)
		)
	}

	@Callback
	fun onAutoUploadChooseLocation(result: ActivityResult, vaultModel: VaultModel?) {
		location = result.singleResult as CloudFolderModel
		location?.let { sharedPreferencesHandler.photoUploadVaultFolder(it.path) }
		location?.let { view?.showChosenLocation(it) }
	}

	enum class AuthenticationState {
		CHOOSE_LOCATION, INIT_ROOT
	}

	init {
		unsubscribeOnDestroy(getVaultListUseCase, getRootFolderUseCase, getDecryptedCloudForVaultUseCase)
	}
}
