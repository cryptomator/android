package org.cryptomator.presentation.presenter

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.pcloud.sdk.AuthorizationActivity
import com.pcloud.sdk.AuthorizationData
import com.pcloud.sdk.AuthorizationRequest
import com.pcloud.sdk.AuthorizationResult
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.LocalStorageCloud
import org.cryptomator.domain.PCloud
import org.cryptomator.domain.Vault
import org.cryptomator.domain.di.PerView
import org.cryptomator.domain.usecases.cloud.AddOrChangeCloudConnectionUseCase
import org.cryptomator.domain.usecases.cloud.GetCloudsUseCase
import org.cryptomator.domain.usecases.cloud.GetUsernameUseCase
import org.cryptomator.domain.usecases.cloud.RemoveCloudUseCase
import org.cryptomator.domain.usecases.vault.DeleteVaultUseCase
import org.cryptomator.domain.usecases.vault.GetVaultListUseCase
import org.cryptomator.generator.Callback
import org.cryptomator.presentation.BuildConfig
import org.cryptomator.presentation.R
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.intent.Intents
import org.cryptomator.presentation.model.CloudModel
import org.cryptomator.presentation.model.CloudTypeModel
import org.cryptomator.presentation.model.LocalStorageModel
import org.cryptomator.presentation.model.S3CloudModel
import org.cryptomator.presentation.model.WebDavCloudModel
import org.cryptomator.presentation.model.mappers.CloudModelMapper
import org.cryptomator.presentation.ui.activity.view.CloudConnectionListView
import org.cryptomator.presentation.workflow.ActivityResult
import org.cryptomator.util.crypto.CredentialCryptor
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import timber.log.Timber

@PerView
class CloudConnectionListPresenter @Inject constructor( //
	private val getCloudsUseCase: GetCloudsUseCase,  //
	private val getUsernameUseCase: GetUsernameUseCase, //
	private val removeCloudUseCase: RemoveCloudUseCase,  //
	private val addOrChangeCloudConnectionUseCase: AddOrChangeCloudConnectionUseCase,  //
	private val getVaultListUseCase: GetVaultListUseCase,  //
	private val deleteVaultUseCase: DeleteVaultUseCase,  //
	private val cloudModelMapper: CloudModelMapper,  //
	exceptionMappings: ExceptionHandlers
) : Presenter<CloudConnectionListView>(exceptionMappings) {

	private val selectedCloudType = AtomicReference<CloudTypeModel>()
	private var defaultLocalStorageCloud: LocalStorageCloud? = null
	fun setSelectedCloudType(selectedCloudType: CloudTypeModel) {
		this.selectedCloudType.set(selectedCloudType)
	}

	fun loadCloudList() {
		getCloudsUseCase //
			.withCloudType(CloudTypeModel.valueOf(selectedCloudType.get())) //
			.run(object : DefaultResultHandler<List<Cloud>>() {
				override fun onSuccess(clouds: List<Cloud>) {
					val cloudModels: MutableList<CloudModel> = ArrayList()
					clouds.forEach { cloud ->
						if (CloudTypeModel.LOCAL == selectedCloudType.get()) {
							if ((cloud as LocalStorageCloud).rootUri() == null) {
								defaultLocalStorageCloud = cloud
								return@forEach
							}
						}
						cloudModels.add(cloudModelMapper.toModel(cloud))
					}
					view?.showCloudModels(cloudModels)
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
		return allVaults.filterTo(ArrayList()) { it.cloud.type() == cloudModel.toCloud().type() }
	}

	fun onDeleteCloudConnectionAndVaults(cloudModel: CloudModel, vaultsOfCloud: ArrayList<Vault>) {
		vaultsOfCloud.forEach { vault ->
			deleteVault(vault)
		}
		deleteCloud(cloudModel)
	}

	private fun deleteVault(vault: Vault) {
		deleteVaultUseCase.withVault(vault).run(DefaultResultHandler())
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
			CloudTypeModel.WEBDAV -> requestActivityResult(
				ActivityResultCallbacks.addChangeMultiCloud(),  //
				Intents.webDavAddOrChangeIntent()
			)
			CloudTypeModel.PCLOUD -> {
				val authIntent: Intent = AuthorizationActivity.createIntent(
					this.context(),
					AuthorizationRequest.create()
						.setType(AuthorizationRequest.Type.TOKEN)
						.setClientId(BuildConfig.PCLOUD_CLIENT_ID)
						.setForceAccessApproval(true)
						.addPermission("manageshares")
						.build()
				)
				requestActivityResult(
					ActivityResultCallbacks.pCloudAuthenticationFinished(),  //
					authIntent
				)
			}
			CloudTypeModel.S3 -> requestActivityResult(
				ActivityResultCallbacks.addChangeMultiCloud(),  //
				Intents.s3AddOrChangeIntent()
			)
			CloudTypeModel.LOCAL -> openDocumentTree()
		}
	}

	@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
	private fun openDocumentTree() {
		try {
			requestActivityResult( //
				ActivityResultCallbacks.pickedLocalStorageLocation(),  //
				Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
			)
		} catch (exception: ActivityNotFoundException) {
			Toast //
				.makeText( //
					activity().applicationContext,  //
					context().getText(R.string.screen_cloud_local_error_no_content_provider),  //
					Toast.LENGTH_SHORT
				) //
				.show()
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
		val authData: AuthorizationData = AuthorizationActivity.getResult(activityResult.intent())
		val result: AuthorizationResult = authData.result

		when (result) {
			AuthorizationResult.ACCESS_GRANTED -> {
				val accessToken: String = CredentialCryptor //
					.getInstance(this.context()) //
					.encrypt(authData.token)
				val pCloudSkeleton: PCloud = PCloud.aPCloud() //
					.withAccessToken(accessToken)
					.withUrl(authData.apiHost)
					.build();
				getUsernameUseCase //
					.withCloud(pCloudSkeleton) //
					.run(object : DefaultResultHandler<String>() {
						override fun onSuccess(username: String?) {
							prepareForSavingPCloud(PCloud.aCopyOf(pCloudSkeleton).withUsername(username).build())
						}
					})
			}
			AuthorizationResult.ACCESS_DENIED -> {
				Timber.tag("CloudConnListPresenter").e("Account access denied")
				view?.showMessage(String.format(getString(R.string.screen_authenticate_auth_authentication_failed), getString(R.string.cloud_names_pcloud)))
			}
			AuthorizationResult.AUTH_ERROR -> {
				Timber.tag("CloudConnListPresenter").e("""Account access grant error: ${authData.errorMessage}""".trimIndent())
				view?.showMessage(String.format(getString(R.string.screen_authenticate_auth_authentication_failed), getString(R.string.cloud_names_pcloud)))
			}
			AuthorizationResult.CANCELLED -> {
				Timber.tag("CloudConnListPresenter").i("Account access grant cancelled")
				view?.showMessage(String.format(getString(R.string.screen_authenticate_auth_authentication_failed), getString(R.string.cloud_names_pcloud)))
			}
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
						it as PCloud
						saveCloud(
							PCloud.aCopyOf(it) //
								.withUrl(cloud.url())
								.withAccessToken(cloud.accessToken())
								.build()
						)
					} ?: saveCloud(cloud)
				}
			})
	}

	fun saveCloud(cloud: PCloud) {
		addOrChangeCloudConnectionUseCase //
			.withCloud(cloud) //
			.run(object : DefaultResultHandler<Void?>() {
				override fun onSuccess(void: Void?) {
					loadCloudList()
				}
			})
	}

	@Callback
	@RequiresApi(api = Build.VERSION_CODES.KITKAT)
	fun pickedLocalStorageLocation(result: ActivityResult) {
		val rootTreeUriOfLocalStorage = result.intent().data
		persistUriPermission(rootTreeUriOfLocalStorage)
		addOrChangeCloudConnectionUseCase.withCloud(
			LocalStorageCloud.aLocalStorage() //
				.withRootUri(rootTreeUriOfLocalStorage.toString()) //
				.build()
		) //
			.run(object : DefaultResultHandler<Void?>() {
				override fun onSuccess(void: Void?) {
					loadCloudList()
				}
			})
	}

	@RequiresApi(api = Build.VERSION_CODES.KITKAT)
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

	@RequiresApi(api = Build.VERSION_CODES.KITKAT)
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

	fun onDefaultLocalCloudConnectionClicked() {
		finishWithResult(SELECTED_CLOUD, defaultLocalStorageCloud)
	}

	companion object {

		const val SELECTED_CLOUD = "selectedCloudConnection"
	}

	init {
		unsubscribeOnDestroy(getCloudsUseCase, removeCloudUseCase, addOrChangeCloudConnectionUseCase, getVaultListUseCase, deleteVaultUseCase)
	}
}
