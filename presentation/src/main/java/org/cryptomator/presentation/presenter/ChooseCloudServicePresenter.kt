package org.cryptomator.presentation.presenter

import android.view.View
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.di.PerView
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.domain.usecases.cloud.GetCloudsUseCase
import org.cryptomator.generator.Callback
import org.cryptomator.presentation.R
import org.cryptomator.util.FlavorConfig
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.intent.Intents
import org.cryptomator.presentation.model.CloudTypeModel
import org.cryptomator.presentation.model.mappers.CloudModelMapper
import org.cryptomator.presentation.ui.activity.view.ChooseCloudServiceView
import org.cryptomator.presentation.ui.snackbar.SnackbarAction
import org.cryptomator.presentation.workflow.ActivityResult
import org.cryptomator.presentation.workflow.AddExistingVaultWorkflow
import org.cryptomator.presentation.workflow.CreateNewVaultWorkflow
import org.cryptomator.presentation.workflow.Workflow
import javax.inject.Inject

@PerView
class ChooseCloudServicePresenter @Inject constructor( //
	private val getCloudsUseCase: GetCloudsUseCase,  //
	private val cloudModelMapper: CloudModelMapper,  //
	private val addExistingVaultWorkflow: AddExistingVaultWorkflow,  //
	private val createNewVaultWorkflow: CreateNewVaultWorkflow,  //
	exceptionMappings: ExceptionHandlers
) : Presenter<ChooseCloudServiceView>(exceptionMappings) {

	override fun workflows(): Iterable<Workflow<*>> {
		return listOf(addExistingVaultWorkflow, createNewVaultWorkflow)
	}

	/**
	 * Prepare and deliver the list of cloud providers to the view based on the current flavor configuration.
	 *
	 * Removes the CRYPTO entry unconditionally. If `FlavorConfig.excludesGoogleDrive` is true, removes
	 * GOOGLE_DRIVE; otherwise, when `FlavorConfig.isLiteFlavor` is true, additionally removes DROPBOX,
	 * ONEDRIVE, and PCLOUD. The resulting list is passed to `view?.render`.
	 */
	override fun resumed() {
		val cloudTypeModels: MutableList<CloudTypeModel> = ArrayList(listOf(*CloudTypeModel.values()))
		cloudTypeModels.remove(CloudTypeModel.CRYPTO)

		if (FlavorConfig.excludesGoogleDrive) {
			cloudTypeModels.remove(CloudTypeModel.GOOGLE_DRIVE)
		} else if (FlavorConfig.isLiteFlavor) {
			cloudTypeModels.remove(CloudTypeModel.GOOGLE_DRIVE)
			cloudTypeModels.remove(CloudTypeModel.DROPBOX)
			cloudTypeModels.remove(CloudTypeModel.ONEDRIVE)
			cloudTypeModels.remove(CloudTypeModel.PCLOUD)
		}

		view?.render(cloudTypeModels)
	}

	fun cloudPicked(cloudTypeModel: CloudTypeModel) {
		if (cloudTypeModel.isMultiInstance) {
			handleMultiInstanceClouds(cloudTypeModel)
		} else {
			handleSingleInstanceClouds(cloudTypeModel)
		}
	}

	private fun handleMultiInstanceClouds(cloudTypeModel: CloudTypeModel) {
		startCloudConnectionListActivity(cloudTypeModel)
	}

	private fun startCloudConnectionListActivity(cloudTypeModel: CloudTypeModel) {
		requestActivityResult( //
			ActivityResultCallbacks.cloudConnectionListFinished(),  //
			Intents.cloudConnectionListIntent() //
				.withCloudType(cloudTypeModel) //
				.withDialogTitle(context().getString(R.string.screen_cloud_connections_title)) //
				.withFinishOnCloudItemClick(true)
		)
	}

	@Callback
	fun cloudConnectionListFinished(result: ActivityResult) {
		val cloud = result.intent().getSerializableExtra(CloudConnectionListPresenter.SELECTED_CLOUD) as Cloud
		onCloudSelected(cloud)
	}

	private fun handleSingleInstanceClouds(cloudTypeModel: CloudTypeModel) {
		getCloudsUseCase //
			.withCloudType(CloudTypeModel.valueOf(cloudTypeModel)) //
			.run(object : DefaultResultHandler<List<Cloud>>() {
				override fun onSuccess(clouds: List<Cloud>) {
					if (clouds.size > 1) {
						throw FatalBackendException("More then one cloud")
					}
					onCloudSelected(clouds[0])
				}
			})
	}

	/**
	 * Finish the presenter and deliver the user's selected cloud to the caller.
	 *
	 * @param cloud The selected cloud to return to the caller after mapping.
	 */
	private fun onCloudSelected(cloud: Cloud) {
		finishWithResult(cloudModelMapper.toModel(cloud))
	}

	/**
	 * Shows a snackbar prompting the user about Cryptomator variants in the Lite flavor.
	 *
	 * When the app runs in the Lite variant this displays a snackbar with an action that
	 * opens the Cryptomator variants screen.
	 */
	fun showCloudMissingSnackbarHintInLiteVariant() {
		if (FlavorConfig.isLiteFlavor) {
			view?.showSnackbar(R.string.snack_bar_cryptomator_variants_hint, object : SnackbarAction {
				override fun onClick(v: View?) {
					startIntent(Intents.cryptomatorVariantsIntent())
				}

				override val text: Int
					get() = R.string.snack_bar_cryptomator_variants_title
			})
		}
	}

	init {
		unsubscribeOnDestroy(getCloudsUseCase)
	}
}
