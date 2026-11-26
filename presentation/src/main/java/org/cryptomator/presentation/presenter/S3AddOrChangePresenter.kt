package org.cryptomator.presentation.presenter

import android.widget.Toast
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.S3Cloud
import org.cryptomator.domain.S3Provider
import org.cryptomator.domain.di.PerView
import org.cryptomator.domain.exception.authentication.LocalNetworkPermissionMissingException
import org.cryptomator.domain.usecases.cloud.AddOrChangeCloudConnectionUseCase
import org.cryptomator.domain.usecases.cloud.ConnectToS3UseCase
import org.cryptomator.generator.Callback
import org.cryptomator.presentation.R
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.model.ProgressStateModel
import org.cryptomator.presentation.ui.activity.view.S3AddOrChangeView
import org.cryptomator.presentation.util.ServerUrl
import org.cryptomator.presentation.workflow.PermissionsResult
import org.cryptomator.util.ExceptionUtil
import org.cryptomator.util.crypto.CredentialCryptor
import javax.inject.Inject

@PerView
class S3AddOrChangePresenter @Inject internal constructor( //
	private val addOrChangeCloudConnectionUseCase: AddOrChangeCloudConnectionUseCase,  //
	private val connectToS3UseCase: ConnectToS3UseCase,  //
	exceptionMappings: ExceptionHandlers
) : Presenter<S3AddOrChangeView>(exceptionMappings) {

	private var s3Provider: S3Provider? = null

	fun setS3Provider(s3Provider: S3Provider?) {
		this.s3Provider = s3Provider
	}

	fun checkProviderInput(accessKey: String, secretKey: String, bucket: String, server: String, accountId: String, selectedChoice: Int?, otherChoice: String, displayName: String) {
		val provider = s3Provider ?: throw IllegalStateException("No S3 provider set")
		val choice = selectedChoice?.let { provider.choices.getOrNull(it) } ?: otherChoice
		val endpoint = provider.endpoint(ServerUrl.parse(server).orEmpty(), accountId, choice)
		val region = provider.region(choice)
		val statusMessage = when {
			provider.requiresServer && server.isEmpty() -> getString(R.string.screen_s3_settings_msg_server_not_empty)
			provider.requiresAccountId && accountId.isEmpty() -> getString(R.string.screen_s3_settings_msg_account_id_not_empty)
			provider.choices.isNotEmpty() && choice.isEmpty() -> getString(if (provider.choiceIsEndpointName) R.string.screen_s3_settings_msg_endpoint_name_not_empty else R.string.screen_s3_settings_msg_region_not_empty)
			isSchemeAndHost(endpoint) && REGION.matches(region) -> null
			provider.requiresServer && !isSchemeAndHost(endpoint) -> getString(R.string.screen_s3_settings_msg_server_is_invalid)
			provider.requiresAccountId -> getString(R.string.screen_s3_settings_msg_account_id_is_invalid)
			provider.choiceIsEndpointName -> getString(R.string.screen_s3_settings_msg_endpoint_name_is_invalid)
			else -> getString(R.string.screen_s3_settings_msg_region_is_invalid)
		}
		if (statusMessage != null) {
			Toast.makeText(context(), statusMessage, Toast.LENGTH_SHORT).show()
		} else {
			checkUserInput(accessKey, secretKey, bucket, endpoint, region, null, displayName)
		}
	}

	// Equal only for a lower-case scheme://host[:port], because ServerUrl.parse normalizes through OkHttp
	// and rejects query, fragment and user info
	private fun isSchemeAndHost(endpoint: String): Boolean {
		return ServerUrl.parse(endpoint) == "$endpoint/"
	}

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
		if (endpoint.isNullOrEmpty() || region.isNullOrEmpty()) {
			statusMessage = getString(R.string.screen_s3_settings_msg_endpoint_and_region_not_empty)
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
			.withDisplayName(displayName) //
			.withProvider(s3Provider)

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
					if (ExceptionUtil.contains(e, LocalNetworkPermissionMissingException::class.java)) {
						requestLocalNetworkPermission(PermissionsResultCallbacks.s3LocalNetworkPermissionResult(cloud))
					} else {
						super.onError(e)
					}
				}
			})
	}

	@Callback
	fun s3LocalNetworkPermissionResult(result: PermissionsResult, cloud: S3Cloud) {
		if (result.granted()) {
			authenticate(cloud)
		}
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

	companion object {

		// same rule as MinIO's Utils.REGION_REGEX, the S3 client throws for any other region
		private val REGION = Regex("(?![_-])[a-z_\\d-]{1,63}(?<![_-])", RegexOption.IGNORE_CASE)
	}
}
