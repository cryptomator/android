package org.cryptomator.presentation.presenter

import android.widget.Toast
import org.cryptomator.data.cloud.smb.SmbLocation
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.SmbCloud
import org.cryptomator.domain.di.PerView
import org.cryptomator.domain.exception.authentication.LocalNetworkPermissionMissingException
import org.cryptomator.domain.usecases.cloud.AddOrChangeCloudConnectionUseCase
import org.cryptomator.domain.usecases.cloud.ConnectToSmbUseCase
import org.cryptomator.generator.Callback
import org.cryptomator.presentation.R
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.model.ProgressStateModel
import org.cryptomator.presentation.ui.activity.view.SmbAddOrChangeView
import org.cryptomator.presentation.workflow.PermissionsResult
import org.cryptomator.util.ExceptionUtil
import org.cryptomator.util.crypto.CredentialCryptor
import java.net.UnknownHostException
import javax.inject.Inject

@PerView
class SmbAddOrChangePresenter @Inject internal constructor( //
	private val addOrChangeCloudConnectionUseCase: AddOrChangeCloudConnectionUseCase,  //
	private val connectToSmbUseCase: ConnectToSmbUseCase,  //
	exceptionMappings: ExceptionHandlers
) : Presenter<SmbAddOrChangeView>(exceptionMappings) {

	fun checkUserInput(url: String, username: String, password: String, cloudId: Long?) {
		val normalizedUrl = SmbLocation.normalize(url)
		val statusMessage = when {
			normalizedUrl == SmbLocation.SCHEME -> getString(R.string.screen_smb_settings_msg_url_must_not_be_empty)
			SmbLocation.parse(normalizedUrl) == null -> getString(R.string.screen_smb_settings_msg_url_is_invalid)
			username.isEmpty() -> getString(R.string.screen_smb_settings_msg_username_must_not_be_empty)
			password.isEmpty() -> getString(R.string.screen_smb_settings_msg_password_must_not_be_empty)
			else -> null
		}
		if (statusMessage != null) {
			Toast.makeText(context(), statusMessage, Toast.LENGTH_SHORT).show()
		} else {
			view?.onCheckUserInputSucceeded(normalizedUrl, username, encryptPassword(password), cloudId)
		}
	}

	private fun encryptPassword(password: String): String {
		return CredentialCryptor //
			.getInstance(context()) //
			.encrypt(password)
	}

	fun authenticate(url: String, username: String, password: String, cloudId: Long?) {
		val cloud = SmbCloud.aSmbCloud() //
			.withId(cloudId) //
			.withUrl(url) //
			.withUsername(username) //
			.withPassword(password) //
			.build()
		authenticate(cloud)
	}

	private fun authenticate(cloud: SmbCloud) {
		view?.showProgress(ProgressModel(ProgressStateModel.AUTHENTICATION))
		connectToSmbUseCase //
			.withCloud(cloud) //
			.run(object : DefaultResultHandler<Void?>() {
				override fun onSuccess(void: Void?) {
					save(cloud)
				}

				override fun onError(e: Throwable) {
					view?.showProgress(ProgressModel.COMPLETED)
					if (ExceptionUtil.contains(e, LocalNetworkPermissionMissingException::class.java)) {
						requestLocalNetworkPermission(PermissionsResultCallbacks.smbLocalNetworkPermissionResult(cloud))
					} else if (ExceptionUtil.contains(e, UnknownHostException::class.java)) {
						view?.showError(R.string.error_server_not_found)
					} else {
						super.onError(e)
					}
				}
			})
	}

	@Callback
	fun smbLocalNetworkPermissionResult(result: PermissionsResult, cloud: SmbCloud) {
		if (result.granted()) {
			authenticate(cloud)
		}
	}

	private fun save(cloud: Cloud) {
		addOrChangeCloudConnectionUseCase //
			.withCloud(cloud) //
			.run(object : DefaultResultHandler<Void?>() {
				override fun onSuccess(result: Void?) {
					finishWithResult(CloudConnectionListPresenter.SELECTED_CLOUD, cloud)
				}

				override fun onError(e: Throwable) {
					view?.showProgress(ProgressModel.COMPLETED)
					super.onError(e)
				}
			})
	}

	init {
		unsubscribeOnDestroy(addOrChangeCloudConnectionUseCase, connectToSmbUseCase)
	}
}
