package org.cryptomator.presentation.presenter

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import org.cryptomator.domain.BoxCloud
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.LocalStorageCloud
import org.cryptomator.domain.OnedriveCloud
import org.cryptomator.domain.PCloud
import org.cryptomator.domain.SharepointCloud
import org.cryptomator.domain.Vault
import org.cryptomator.domain.di.PerView
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.domain.exception.NetworkConnectionException
import org.cryptomator.domain.exception.NoSuchCloudFileException
import org.cryptomator.domain.usecases.cloud.AddOrChangeCloudConnectionUseCase
import org.cryptomator.domain.usecases.cloud.AuthenticateBoxUseCase
import org.cryptomator.domain.usecases.cloud.GetCloudsUseCase
import org.cryptomator.domain.usecases.cloud.GetSharepointDrivesUseCase
import org.cryptomator.domain.usecases.cloud.GetUsernameUseCase
import org.cryptomator.domain.usecases.cloud.RemoveCloudUseCase
import org.cryptomator.domain.usecases.vault.DeleteVaultsUseCase
import org.cryptomator.domain.usecases.vault.GetVaultListUseCase
import org.cryptomator.generator.Callback
import org.cryptomator.presentation.R
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.intent.Intents
import org.cryptomator.presentation.model.CloudModel
import org.cryptomator.presentation.model.CloudTypeModel
import org.cryptomator.presentation.model.LocalStorageModel
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.model.ProgressStateModel
import org.cryptomator.presentation.model.S3CloudModel
import org.cryptomator.presentation.model.WebDavCloudModel
import org.cryptomator.presentation.model.mappers.CloudModelMapper
import org.cryptomator.presentation.ui.activity.view.CloudConnectionListView
import org.cryptomator.presentation.ui.dialog.ChooseSharepointDriveDialog
import org.cryptomator.presentation.ui.dialog.EnterSharepointUrlDialog
import org.cryptomator.presentation.ui.dialog.PCloudCredentialsUpdatedDialog
import org.cryptomator.presentation.workflow.ActivityResult
import org.cryptomator.util.ExceptionUtil
import org.cryptomator.util.crypto.CredentialCryptor
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlin.jvm.optionals.getOrNull
import timber.log.Timber

@PerView
class CloudConnectionListPresenter @Inject constructor( //
	private val getCloudsUseCase: GetCloudsUseCase,  //
	private val getUsernameUseCase: GetUsernameUseCase, //
	private val getSharepointDrivesUseCase: GetSharepointDrivesUseCase, //
	private val authenticateBoxUseCase: AuthenticateBoxUseCase, //
	private val removeCloudUseCase: RemoveCloudUseCase,  //
	private val addOrChangeCloudConnectionUseCase: AddOrChangeCloudConnectionUseCase,  //
	private val getVaultListUseCase: GetVaultListUseCase,  //
	private val deleteVaultsUseCase: DeleteVaultsUseCase,  //
	private val cloudModelMapper: CloudModelMapper,  //
	exceptionMappings: ExceptionHandlers
) : Presenter<CloudConnectionListView>(exceptionMappings) {

	private val selectedCloudType = AtomicReference<CloudTypeModel>()
	private var pendingSharepointDrives: List<SharepointCloud>? = null

	fun setSelectedCloudType(selectedCloudType: CloudTypeModel) {
		this.selectedCloudType.set(selectedCloudType)
	}

	fun loadCloudList() {
		getCloudsUseCase //
			.withCloudType(CloudTypeModel.valueOf(selectedCloudType.get())) //
			.run(object : DefaultResultHandler<List<Cloud>>() {
				override fun onSuccess(clouds: List<Cloud>) {
					view?.showCloudModels(clouds.map { cloud -> cloudModelMapper.toModel(cloud) })
				}
			})
	}

	fun onDeleteCloudClicked(cloudModel: CloudModel) {
		getVaultListUseCase.run(object : DefaultResultHandler<List<Vault>>() {
			override fun onSuccess(vaults: List<Vault>) {
				val vaultsOfCloud = vaultsFor(cloudModel, vaults)
				if (vaultsOfCloud.isEmpty()) {
					deleteCloud(cloudModel)
				} else {
					view?.showCloudConnectionHasVaultsDialog(cloudModel, vaultsOfCloud)
				}
			}
		})
	}

	private fun vaultsFor(cloudModel: CloudModel, allVaults: List<Vault>): ArrayList<Vault> {
		return allVaults.filterTo(ArrayList()) { it.cloud.id() == cloudModel.toCloud().id() }
	}

	fun onDeleteCloudConnectionAndVaults(cloudModel: CloudModel, vaultsOfCloud: ArrayList<Vault>) {
		if (vaultsOfCloud.isEmpty()) {
			deleteCloud(cloudModel)
		} else {
			deleteVaultsUseCase
				.withVaults(vaultsOfCloud)
				.run(object : DefaultResultHandler<List<Long>>() {
					override fun onFinished() {
						deleteCloud(cloudModel)
					}

					override fun onError(e: Throwable) {
						Timber.tag("CloudConnectionListPresenter").e(e, "Failed to remove all vaults")
					}
				})
		}
	}

	private fun deleteCloud(cloudModel: CloudModel) {
		if (cloudModel.cloudType() == CloudTypeModel.LOCAL) {
			releaseUriPermissionForLocalStorageCloud(cloudModel as LocalStorageModel)
		}
		deleteCloud(cloudModel.toCloud())
	}

	private fun releaseUriPermissionForLocalStorageCloud(cloudModel: LocalStorageModel) {
		if ((cloudModel.toCloud() as LocalStorageCloud).rootUri() != null) {
			releaseUriPermission(cloudModel.uri())
		}
	}

	private fun deleteCloud(cloud: Cloud) {
		removeCloudUseCase //
			.withCloud(cloud) //
			.run(object : DefaultResultHandler<Void?>() {
				override fun onSuccess(ignore: Void?) {
					loadCloudList()
				}
			})
	}

	fun onAddConnectionClicked() {
		when (selectedCloudType.get()) {
			CloudTypeModel.ONEDRIVE -> addOnedriveCloud()
			CloudTypeModel.SHAREPOINT -> view?.showDialog(EnterSharepointUrlDialog.newInstance())
			CloudTypeModel.WEBDAV -> requestActivityResult(ActivityResultCallbacks.addChangeMultiCloud(), Intents.webDavAddOrChangeIntent())
			CloudTypeModel.PCLOUD -> requestActivityResult(ActivityResultCallbacks.pCloudAuthenticationFinished(), Intents.authenticatePCloudIntent())
			CloudTypeModel.BOX -> requestActivityResult(ActivityResultCallbacks.boxAuthenticationFinished(), Intents.authenticateBoxIntent())
			CloudTypeModel.S3 -> requestActivityResult(ActivityResultCallbacks.addChangeMultiCloud(), Intents.s3AddOrChangeIntent())
			CloudTypeModel.LOCAL -> openDocumentTree()
			else -> throw IllegalStateException("Cloud type is not supported")
		}
	}

	private fun addOnedriveCloud() {
		MicrosoftGraphAuthentication.getAuthenticatedOnedriveCloud(activity(), { cloud ->
			saveOnedriveCloud(cloud)
		}, ::showAuthenticationError)
	}

	private fun showAuthenticationError(e: FatalBackendException) {
		ExceptionUtil.extract(e, NetworkConnectionException::class.java).getOrNull()?.let { showError(it) } ?: showError(e)
	}

	private fun saveOnedriveCloud(onedriveSkeleton: OnedriveCloud) {
		getUsernameUseCase //
			.withCloud(onedriveSkeleton) //
			.run(object : DefaultResultHandler<String>() {
				override fun onSuccess(username: String) {
					prepareForSavingOnedriveCloud(OnedriveCloud.aCopyOf(onedriveSkeleton).withUsername(username).build())
				}
			})
	}

	fun prepareForSavingOnedriveCloud(cloud: OnedriveCloud) {
		getCloudsUseCase //
			.withCloudType(CloudTypeModel.valueOf(selectedCloudType.get())) //
			.run(object : DefaultResultHandler<List<Cloud>>() {
				override fun onSuccess(clouds: List<Cloud>) {
					clouds.firstOrNull {
						(it as OnedriveCloud).username() == cloud.username()
					}?.let {
						saveCloud(OnedriveCloud.aCopyOf(it as OnedriveCloud).withAccessToken(cloud.accessToken()).build())
						Timber.tag("CloudConnListPresenter").i("OneDrive access token updated")
					} ?: saveCloud(cloud)
				}
			})
	}

	fun onSharepointUrlEntered(siteUrl: String) {
		MicrosoftGraphAuthentication.getAuthenticatedSharepointCloud(activity(), siteUrl, { cloud ->
			loadSharepointDrives(cloud)
		}, ::showAuthenticationError)
	}

	internal fun loadSharepointDrives(sharepointSkeleton: SharepointCloud) {
		showProgress(ProgressModel(ProgressStateModel.AUTHENTICATION))
		getSharepointDrivesUseCase //
			.withCloud(sharepointSkeleton) //
			.run(object : DefaultResultHandler<List<SharepointCloud>>() {
				override fun onSuccess(drives: List<SharepointCloud>) {
					// complete before showing the picker, since showProgress replaces any dialog that isn't ProgressAware
					view?.showProgress(ProgressModel.COMPLETED)
					if (isPaused) {
						pendingSharepointDrives = drives
					} else {
						showSharepointDrives(drives)
					}
				}

				override fun onError(e: Throwable) {
					view?.showProgress(ProgressModel.COMPLETED)
					if (e is NoSuchCloudFileException) {
						view?.showError(R.string.screen_cloud_connections_msg_sharepoint_site_not_found)
					} else {
						showError(e)
					}
				}
			})
	}

	override fun resumed() {
		pendingSharepointDrives?.let { drives ->
			pendingSharepointDrives = null
			showSharepointDrives(drives)
		}
	}

	private fun showSharepointDrives(drives: List<SharepointCloud>) {
		if (drives.isEmpty()) {
			view?.showMessage(R.string.screen_cloud_connections_msg_no_sharepoint_drives)
		} else {
			view?.showDialog(ChooseSharepointDriveDialog.newInstance(ArrayList(drives)))
		}
	}

	fun onSharepointDriveChosen(sharepointSkeleton: SharepointCloud) {
		showProgress(ProgressModel(ProgressStateModel.AUTHENTICATION))
		getUsernameUseCase //
			.withCloud(sharepointSkeleton) //
			.run(object : ProgressCompletingResultHandler<String>() {
				override fun onSuccess(username: String) {
					prepareForSavingSharepointCloud(SharepointCloud.aCopyOf(sharepointSkeleton).withUsername(username).build())
				}
			})
	}

	private fun prepareForSavingSharepointCloud(cloud: SharepointCloud) {
		getCloudsUseCase //
			.withCloudType(CloudTypeModel.valueOf(selectedCloudType.get())) //
			.run(object : DefaultResultHandler<List<Cloud>>() {
				override fun onSuccess(clouds: List<Cloud>) {
					clouds.firstOrNull {
						it.configurationMatches(cloud)
					}?.let {
						saveCloud(SharepointCloud.aCopyOf(it as SharepointCloud).withAccessToken(cloud.accessToken()).build())
						Timber.tag("CloudConnListPresenter").i("SharePoint access token updated")
					} ?: saveCloud(cloud)
				}
			})
	}

	private fun openDocumentTree() {
		try {
			requestActivityResult(ActivityResultCallbacks.pickedLocalStorageLocationForLocalCloud(), Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
		} catch (exception: ActivityNotFoundException) {
			Toast.makeText(activity().applicationContext, context().getText(R.string.screen_cloud_local_error_no_content_provider), Toast.LENGTH_SHORT).show()
			Timber.tag("CloudConnListPresenter").e(exception, "No ContentProvider on system")
		}
	}

	fun onChangeCloudClicked(cloudModel: CloudModel) {
		when {
			cloudModel.cloudType() == CloudTypeModel.WEBDAV -> {
				requestActivityResult(
					ActivityResultCallbacks.addChangeMultiCloud(),  //
					Intents.webDavAddOrChangeIntent() //
						.withWebDavCloud(cloudModel as WebDavCloudModel)
				)
			}
			cloudModel.cloudType() == CloudTypeModel.S3 -> {
				requestActivityResult(
					ActivityResultCallbacks.addChangeMultiCloud(),  //
					Intents.s3AddOrChangeIntent() //
						.withS3Cloud(cloudModel as S3CloudModel)
				)
			}
			else -> {
				throw IllegalStateException("Change cloud with type " + cloudModel.cloudType() + " is not supported")
			}
		}
	}

	fun onNodeSettingsClicked(cloudModel: CloudModel) {
		view?.showNodeSettings(cloudModel)
	}

	@Callback
	fun addChangeMultiCloud(result: ActivityResult?) {
		loadCloudList()
	}

	@Callback
	fun pCloudAuthenticationFinished(activityResult: ActivityResult) {
		val code = activityResult.intent().extras?.getString(PCLOUD_OAUTH_AUTH_CODE, "")
		val hostname = activityResult.intent().extras?.getString(PCLOUD_HOSTNAME, "")

		if (!code.isNullOrEmpty() && !hostname.isNullOrEmpty()) {
			Timber.tag("CloudConnectionListPresenter").i("PCloud OAuth code successfully retrieved")
			val accessToken = CredentialCryptor.getInstance(this.context()).encrypt(code)
			val pCloudSkeleton = PCloud.aPCloud().withAccessToken(accessToken).withUrl(hostname).build();
			getUsernameUseCase //
				.withCloud(pCloudSkeleton) //
				.run(object : DefaultResultHandler<String>() {
					override fun onSuccess(username: String) {
						prepareForSavingPCloud(PCloud.aCopyOf(pCloudSkeleton).withUsername(username).build())
					}
				})
		} else {
			Timber.tag("CloudConnectionListPresenter").i("PCloud Authentication not successful")
		}
	}

	fun prepareForSavingPCloud(cloud: PCloud) {
		getCloudsUseCase //
			.withCloudType(CloudTypeModel.valueOf(selectedCloudType.get())) //
			.run(object : DefaultResultHandler<List<Cloud>>() {
				override fun onSuccess(clouds: List<Cloud>) {
					clouds.firstOrNull {
						(it as PCloud).username() == cloud.username()
					}?.let {
						saveCloud(PCloud.aCopyOf(it as PCloud).withUrl(cloud.url()).withAccessToken(cloud.accessToken()).build())
						view?.showDialog(PCloudCredentialsUpdatedDialog.newInstance(it.username()))
					} ?: saveCloud(cloud)
				}
			})
	}

	@Callback
	fun boxAuthenticationFinished(activityResult: ActivityResult) {
		val code = activityResult.intent().extras?.getString(BOX_OAUTH_AUTH_CODE)
		if (code.isNullOrEmpty()) {
			Timber.tag("CloudConnListPresenter").i("Box authentication not successful")
			return
		}
		showProgress(ProgressModel(ProgressStateModel.AUTHENTICATION))
		authenticateBoxUseCase //
			.withAuthorizationCode(code) //
			.run(object : ProgressCompletingResultHandler<BoxCloud>() {
				override fun onSuccess(cloud: BoxCloud) {
					loadCloudList()
				}
			})
	}

	fun saveCloud(cloud: Cloud) {
		addOrChangeCloudConnectionUseCase //
			.withCloud(cloud) //
			.run(object : DefaultResultHandler<Void?>() {
				override fun onSuccess(void: Void?) {
					loadCloudList()
				}
			})
	}

	@Callback
	fun pickedLocalStorageLocationForLocalCloud(result: ActivityResult) {
		val rootTreeUriOfLocalStorage = result.intent().data
		persistUriPermission(rootTreeUriOfLocalStorage)
		addOrChangeCloudConnectionUseCase
			.withCloud(LocalStorageCloud.aLocalStorage().withRootUri(rootTreeUriOfLocalStorage.toString()).build())
			.run(object : DefaultResultHandler<Void?>() {
				override fun onSuccess(void: Void?) {
					loadCloudList()
				}
			})
	}

	private fun persistUriPermission(rootTreeUriOfLocalStorage: Uri?) {
		rootTreeUriOfLocalStorage?.let {
			context() //
				.contentResolver //
				.takePersistableUriPermission( //
					it,  //
					Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
				)
		}
	}

	private fun releaseUriPermission(uri: String) {
		context() //
			.contentResolver //
			.releasePersistableUriPermission( //
				Uri.parse(uri),  //
				Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
			)
	}

	fun onCloudConnectionClicked(cloudModel: CloudModel) {
		if (view?.isFinishOnNodeClicked == true) {
			finishWithResult(SELECTED_CLOUD, cloudModel.toCloud())
		}
	}

	companion object {

		const val SELECTED_CLOUD = "selectedCloudConnection"
		const val PCLOUD_OAUTH_AUTH_CODE = "pCloudOAuthCode"
		const val PCLOUD_HOSTNAME = "pCloudHostname"
		const val BOX_OAUTH_AUTH_CODE = "boxOAuthCode"

	}

	init {
		unsubscribeOnDestroy(getCloudsUseCase, getSharepointDrivesUseCase, authenticateBoxUseCase, removeCloudUseCase, addOrChangeCloudConnectionUseCase, getVaultListUseCase, deleteVaultsUseCase)
	}
}
