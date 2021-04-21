package org.cryptomator.presentation.presenter

import android.widget.Toast
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.S3Cloud
import org.cryptomator.domain.di.PerView
import org.cryptomator.domain.usecases.cloud.AddOrChangeCloudConnectionUseCase
import org.cryptomator.domain.usecases.cloud.ConnectToS3UseCase
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

	fun checkUserInput(accessKey: String, secretKey: String, bucket: String, endpoint: String?, region: String?, cloudId: Long?) {
		var statusMessage: String? = null

		/*if (accessKey.isEmpty()) {
			statusMessage = getString(R.string.screen_webdav_settings_msg_password_must_not_be_empty)
		}
		if (secretKey.isEmpty()) {
			statusMessage = getString(R.string.screen_webdav_settings_msg_username_must_not_be_empty)
		}
		if (bucket.isEmpty()) {
			statusMessage = getString(R.string.screen_webdav_settings_msg_url_must_not_be_empty)
		}*/ // FIXME define what is required

		if (statusMessage != null) {
			// FIXME showError instead of displaying a toast
			Toast.makeText(context(), statusMessage, Toast.LENGTH_SHORT).show()
		} else {
			view?.onCheckUserInputSucceeded(encrypt(accessKey), encrypt(secretKey), bucket, endpoint, region, cloudId)
		}
	}

	private fun encrypt(text: String): String {
		return CredentialCryptor //
				.getInstance(context()) //
				.encrypt(text)
	}

	private fun mapToCloud(accessKey: String, secretKey: String, bucket: String, endpoint: String?, region: String?, cloudId: Long?): S3Cloud {
		var builder = S3Cloud //
				.aS3Cloud() //
				.withAccessKey(accessKey) //
				.withSecretKey(secretKey) //
				.withS3Bucket(bucket) //
				.withS3Endpoint(endpoint) //
				.withS3Region(region)

		cloudId?.let { builder = builder.withId(cloudId) }

		return builder.build()
	}

	fun authenticate(accessKey: String, secretKey: String, bucket: String, endpoint: String?, region: String?, cloudId: Long?) {
		authenticate(mapToCloud(accessKey, secretKey, bucket, endpoint, region, cloudId))
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
