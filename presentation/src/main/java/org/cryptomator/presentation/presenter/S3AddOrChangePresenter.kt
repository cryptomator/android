package org.cryptomator.presentation.presenter

import android.widget.Toast
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.S3Cloud
import org.cryptomator.domain.di.PerView
import org.cryptomator.domain.usecases.cloud.AddOrChangeCloudConnectionUseCase
import org.cryptomator.domain.usecases.cloud.ConnectToS3UseCase
import org.cryptomator.presentation.R
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.model.ProgressStateModel
import org.cryptomator.presentation.ui.activity.view.S3AddOrChangeView
import org.cryptomator.util.crypto.CredentialCryptor
import javax.inject.Inject

@PerView
class S3AddOrChangePresenter @Inject internal constructor( //
		private val addOrChangeCloudConnectionUseCase: AddOrChangeCloudConnectionUseCase,  //
		private val connectToS3UseCase: ConnectToS3UseCase,  //
		exceptionMappings: ExceptionHandlers) : Presenter<S3AddOrChangeView>(exceptionMappings) {

	fun checkUserInput(accessKey: String, secretKey: String, bucket: String, endpoint: String?, region: String?, cloudId: Long?, displayName: String) {
		var statusMessage: String? = null

		if (accessKey.isEmpty()) {
			statusMessage = getString(R.string.screen_s3_settings_msg_access_key_not_empty)
		}
		if (secretKey.isEmpty()) {
			statusMessage = getString(R.string.screen_s3_settings_msg_secret_key_not_empty)
		}
		if (bucket.isEmpty()) {
			statusMessage = getString(R.string.screen_s3_settings_msg_bucket_not_empty)
		}
		if (displayName.isEmpty()) {
			statusMessage = getString(R.string.screen_s3_settings_msg_display_name_not_empty)
		}
		if (endpoint.isNullOrEmpty() && region.isNullOrEmpty()) {
			statusMessage = getString(R.string.screen_s3_settings_msg_endpoint_and_region_not_empty)
		}

		if (!endpoint.isNullOrEmpty() && region.isNullOrEmpty()) {
			statusMessage = getString(R.string.screen_s3_settings_msg_endpoint_set_and_region_empty)
		}

		if (statusMessage != null) {
			Toast.makeText(context(), statusMessage, Toast.LENGTH_SHORT).show()
		} else {
			view?.onCheckUserInputSucceeded(encrypt(accessKey), encrypt(secretKey), bucket, endpoint, region, cloudId, displayName)
		}
	}

	private fun encrypt(text: String): String {
		return CredentialCryptor //
				.getInstance(context()) //
				.encrypt(text)
	}

	private fun mapToCloud(accessKey: String, secretKey: String, bucket: String, endpoint: String?, region: String?, cloudId: Long?, displayName: String): S3Cloud {
		var builder = S3Cloud //
				.aS3Cloud() //
				.withAccessKey(accessKey) //
				.withSecretKey(secretKey) //
				.withS3Bucket(bucket) //
				.withS3Endpoint(endpoint) //
				.withS3Region(region) //
				.withDisplayName(displayName)

		cloudId?.let { builder = builder.withId(cloudId) }

		return builder.build()
	}

	fun authenticate(accessKey: String, secretKey: String, bucket: String, endpoint: String?, region: String?, cloudId: Long?, displayName: String) {
		authenticate(mapToCloud(accessKey, secretKey, bucket, endpoint, region, cloudId, displayName))
	}

	private fun authenticate(cloud: S3Cloud) {
		view?.showProgress(ProgressModel(ProgressStateModel.AUTHENTICATION))
		connectToS3UseCase //
				.withCloud(cloud) //
				.run(object : DefaultResultHandler<Void?>() {
					override fun onSuccess(void: Void?) {
						onCloudAuthenticated(cloud)
					}

					override fun onError(e: Throwable) {
						view?.showProgress(ProgressModel.COMPLETED)
						super.onError(e)
					}
				})
	}

	private fun onCloudAuthenticated(cloud: Cloud) {
		save(cloud)
		finishWithResult(CloudConnectionListPresenter.SELECTED_CLOUD, cloud)
	}

	private fun save(cloud: Cloud) {
		addOrChangeCloudConnectionUseCase //
				.withCloud(cloud) //
				.run(DefaultResultHandler())
	}

	init {
		unsubscribeOnDestroy(addOrChangeCloudConnectionUseCase, connectToS3UseCase)
	}
}
