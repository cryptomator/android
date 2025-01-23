package org.cryptomator.presentation.presenter

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.LocalStorageCloud
import org.cryptomator.domain.OnedriveCloud
import org.cryptomator.domain.PCloud
import org.cryptomator.domain.S3Cloud
import org.cryptomator.domain.WebDavCloud
import org.cryptomator.domain.di.PerView
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.domain.usecases.cloud.GetAllCloudsUseCase
import org.cryptomator.domain.usecases.cloud.GetCloudsUseCase
import org.cryptomator.domain.usecases.cloud.LogoutCloudUseCase
import org.cryptomator.generator.Callback
import org.cryptomator.presentation.BuildConfig
import org.cryptomator.presentation.R
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.intent.Intents
import org.cryptomator.presentation.model.CloudModel
import org.cryptomator.presentation.model.CloudTypeModel
import org.cryptomator.presentation.model.LocalStorageModel
import org.cryptomator.presentation.model.OnedriveCloudModel
import org.cryptomator.presentation.model.PCloudModel
import org.cryptomator.presentation.model.S3CloudModel
import org.cryptomator.presentation.model.WebDavCloudModel
import org.cryptomator.presentation.model.mappers.CloudModelMapper
import org.cryptomator.presentation.ui.activity.view.CloudSettingsView
import org.cryptomator.presentation.workflow.ActivityResult
import java.util.EnumSet
import javax.inject.Inject

@PerView
class CloudSettingsPresenter @Inject constructor( //
	private val getAllCloudsUseCase: GetAllCloudsUseCase,  //
	private val getCloudsUseCase: GetCloudsUseCase,  //
	private val logoutCloudUsecase: LogoutCloudUseCase,  //
	private val cloudModelMapper: CloudModelMapper,  //
	exceptionMappings: ExceptionHandlers
) : Presenter<CloudSettingsView>(exceptionMappings) {

	private val nonSingleLoginClouds: Set<CloudTypeModel> = EnumSet.of( //
		CloudTypeModel.CRYPTO,  //
		CloudTypeModel.LOCAL,  //
		CloudTypeModel.ONEDRIVE,  //
		CloudTypeModel.PCLOUD, //
		CloudTypeModel.S3, //
		CloudTypeModel.WEBDAV
	)

	fun loadClouds() {
		getAllCloudsUseCase.run(CloudsSubscriber())
	}

	fun onCloudClicked(cloudModel: CloudModel) {
		if (cloudModel.cloudType().isMultiInstance) {
			startConnectionListActivity(cloudModel.cloudType())
		} else {
			if (isLoggedIn(cloudModel)) {
				logoutCloudUsecase //
					.withCloud(cloudModel.toCloud()) //
					.run(object : DefaultResultHandler<Cloud>() {
						override fun onSuccess(cloud: Cloud) {
							loadClouds()
						}
					})
			} else {
				loginCloud(cloudModel)
			}
		}
	}

	private fun loginCloud(cloudModel: CloudModel) {
		getCloudsUseCase //
			.withCloudType(CloudTypeModel.valueOf(cloudModel.cloudType())) //
			.run(object : DefaultResultHandler<List<Cloud>>() {
				override fun onSuccess(clouds: List<Cloud>) {
					if (clouds.size > 1) {
						throw FatalBackendException("More then one cloud")
					}
					startAuthentication(clouds[0])
				}
			})
	}

	private fun isLoggedIn(cloudModel: CloudModel): Boolean {
		return cloudModel.username() != null
	}

	private fun startConnectionListActivity(cloudTypeModel: CloudTypeModel) {
		requestActivityResult( //
			ActivityResultCallbacks.webDavConnectionListFinisheds(),  //
			Intents.cloudConnectionListIntent() //
				.withCloudType(cloudTypeModel) //
				.withDialogTitle(effectiveTitle(cloudTypeModel)) //
				.withFinishOnCloudItemClick(false)
		)
	}

	private fun effectiveTitle(cloudTypeModel: CloudTypeModel): String {
		when (cloudTypeModel) {
			CloudTypeModel.ONEDRIVE -> return context().getString(R.string.screen_cloud_settings_onedrive_connections)
			CloudTypeModel.PCLOUD -> return context().getString(R.string.screen_cloud_settings_pcloud_connections)
			CloudTypeModel.WEBDAV -> return context().getString(R.string.screen_cloud_settings_webdav_connections)
			CloudTypeModel.S3 -> return context().getString(R.string.screen_cloud_settings_s3_connections)
			CloudTypeModel.LOCAL -> return context().getString(R.string.screen_cloud_settings_local_storage_locations)
			else -> {}
		}
		return context().getString(R.string.screen_cloud_settings_title)
	}

	@Callback
	fun webDavConnectionListFinisheds(result: ActivityResult) {
		val cloud = result.intent().getSerializableExtra(CloudConnectionListPresenter.SELECTED_CLOUD) as Cloud
		startAuthentication(cloud)
	}

	private fun startAuthentication(cloud: Cloud) {
		requestActivityResult( //
			ActivityResultCallbacks.onCloudAuthenticated(),  //
			Intents.authenticateCloudIntent() //
				.withCloud(cloudModelMapper.toModel(cloud))
		)
	}

	@Callback
	fun onCloudAuthenticated(result: ActivityResult) {
		view?.update(result.getSingleResult(CloudModel::class.java))
	}

	private inner class CloudsSubscriber : DefaultResultHandler<List<Cloud>>() {

		override fun onSuccess(clouds: List<Cloud>) {
			val cloudModel = cloudModelMapper.toModels(clouds) //
				.filter { isSingleLoginCloud(it) } //
				.filter { cloud -> !((BuildConfig.FLAVOR == "fdroid" || BuildConfig.FLAVOR == "accrescent") && cloud.cloudType() == CloudTypeModel.GOOGLE_DRIVE) } //
				.toMutableList() //
				.also {
					it.add(aOnedriveCloud())
					it.add(aPCloud())
					it.add(aWebdavCloud())
					it.add(aS3Cloud())
					it.add(aLocalCloud())
				}
				.filter { cloud -> !(BuildConfig.FLAVOR == "lite" && excludeApiCloudsInLite(cloud.cloudType())) } //
			view?.render(cloudModel)
		}

		private fun excludeApiCloudsInLite(cloudType: CloudTypeModel): Boolean {
			return when (cloudType) {
				CloudTypeModel.GOOGLE_DRIVE -> {
					true
				}
				CloudTypeModel.ONEDRIVE -> {
					true
				}
				CloudTypeModel.DROPBOX -> {
					true
				}
				CloudTypeModel.PCLOUD -> {
					true
				}
				else -> false
			}
		}

		private fun aOnedriveCloud(): OnedriveCloudModel {
			return OnedriveCloudModel(OnedriveCloud.aOnedriveCloud().build())
		}

		private fun aPCloud(): PCloudModel {
			return PCloudModel(PCloud.aPCloud().build())
		}

		private fun aWebdavCloud(): WebDavCloudModel {
			return WebDavCloudModel(WebDavCloud.aWebDavCloudCloud().build())
		}

		private fun aS3Cloud(): S3CloudModel {
			return S3CloudModel(S3Cloud.aS3Cloud().build())
		}

		private fun aLocalCloud(): CloudModel {
			return LocalStorageModel(LocalStorageCloud.aLocalStorage().build())
		}
	}

	private fun isSingleLoginCloud(cloudModel: CloudModel): Boolean {
		return !nonSingleLoginClouds.contains(cloudModel.cloudType())
	}

	init {
		unsubscribeOnDestroy(getAllCloudsUseCase, getCloudsUseCase, logoutCloudUsecase)
	}
}
