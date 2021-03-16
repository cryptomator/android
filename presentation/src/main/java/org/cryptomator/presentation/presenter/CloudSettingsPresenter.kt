package org.cryptomator.presentation.presenter

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.LocalStorageCloud
import org.cryptomator.domain.PCloudCloud
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
import org.cryptomator.presentation.model.PCloudCloudModel
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
		exceptionMappings: ExceptionHandlers) : Presenter<CloudSettingsView>(exceptionMappings) {

	private val nonSingleLoginClouds: Set<CloudTypeModel> = EnumSet.of( //
			CloudTypeModel.CRYPTO,  //
			CloudTypeModel.LOCAL,  //
			CloudTypeModel.PCLOUD, //
			CloudTypeModel.WEBDAV)

	fun loadClouds() {
		getAllCloudsUseCase.run(CloudsSubscriber())
	}

	fun onCloudClicked(cloudModel: CloudModel) {
		if (isWebdavOrPCloudOrLocal(cloudModel)) {
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

	private fun isWebdavOrPCloudOrLocal(cloudModel: CloudModel): Boolean {
		return cloudModel is WebDavCloudModel || cloudModel is LocalStorageModel || cloudModel is PCloudCloudModel
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
						.withFinishOnCloudItemClick(false))
	}

	private fun effectiveTitle(cloudTypeModel: CloudTypeModel): String {
		when (cloudTypeModel) {
			CloudTypeModel.WEBDAV -> return context().getString(R.string.screen_cloud_settings_webdav_connections)
			CloudTypeModel.PCLOUD -> return context().getString(R.string.screen_cloud_settings_pcloud_connections)
			CloudTypeModel.LOCAL -> return context().getString(R.string.screen_cloud_settings_local_storage_locations)
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
						.withCloud(cloudModelMapper.toModel(cloud)))
	}

	@Callback
	fun onCloudAuthenticated(result: ActivityResult) {
		view?.update(result.getSingleResult(CloudModel::class.java))
	}

	private inner class CloudsSubscriber : DefaultResultHandler<List<Cloud>>() {

		override fun onSuccess(clouds: List<Cloud>) {
			val cloudModel = cloudModelMapper.toModels(clouds) //
					.filter { isSingleLoginCloud(it) } //
					.filter { cloud -> !(BuildConfig.FLAVOR == "fdroid" && cloud.cloudType() == CloudTypeModel.GOOGLE_DRIVE) } //
					.toMutableList() //
					.also {
						it.add(aWebdavCloud())
						it.add(aPCloudCloud())
						it.add(aLocalCloud())
					}
			view?.render(cloudModel)
		}

		private fun aWebdavCloud(): WebDavCloudModel {
			return WebDavCloudModel(WebDavCloud.aWebDavCloudCloud().build())
		}

		private fun aPCloudCloud(): PCloudCloudModel {
			return PCloudCloudModel(PCloudCloud.aPCloudCloud().build())
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
