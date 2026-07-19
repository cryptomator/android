package org.cryptomator.presentation.presenter

import android.widget.Toast
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.SmbCloud
import org.cryptomator.domain.di.PerView
import org.cryptomator.domain.usecases.cloud.AddOrChangeCloudConnectionUseCase
import org.cryptomator.domain.usecases.cloud.ConnectToSmbUseCase
import org.cryptomator.presentation.R
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.model.ProgressStateModel
import org.cryptomator.presentation.ui.activity.view.SmbAddOrChangeView
import org.cryptomator.util.crypto.CredentialCryptor
import javax.inject.Inject

@PerView
class SmbAddOrChangePresenter @Inject internal constructor( //
	private val addOrChangeCloudConnectionUseCase: AddOrChangeCloudConnectionUseCase,  //
	private val connectToSmbUseCase: ConnectToSmbUseCase,  //
	exceptionMappings: ExceptionHandlers
) : Presenter<SmbAddOrChangeView>(exceptionMappings) {

	fun checkUserInput(urlPort: String, username: String, password: String, domain: String, cloudId: Long?) {
		var statusMessage: String? = null

		if (password.isEmpty()) {
			statusMessage = getString(R.string.screen_webdav_settings_msg_password_must_not_be_empty)
		}
		if (username.isEmpty()) {
			statusMessage = getString(R.string.screen_webdav_settings_msg_username_must_not_be_empty)
		}
		if (urlPort.isEmpty()) {
			statusMessage = getString(R.string.screen_webdav_settings_msg_url_must_not_be_empty)
		} else if (!isValid(urlPort)) {
			statusMessage = getString(R.string.screen_webdav_settings_msg_url_is_invalid)
		}
		if (statusMessage != null) {
			Toast.makeText(context(), statusMessage, Toast.LENGTH_SHORT).show()
		} else {
			val urlPortWithoutTrailingSlash = if (urlPort.endsWith("/")) urlPort.substring(0, urlPort.length - 1) else urlPort
			val encryptedPassword = encryptPassword(password)
			view?.onCheckUserInputSucceeded(urlPortWithoutTrailingSlash, username, encryptedPassword, domain, cloudId)
		}
	}

	private fun encryptPassword(password: String): String {
		return CredentialCryptor //
			.getInstance(context()) //
			.encrypt(password)
	}

	private fun isValid(urlPort: String): Boolean {
		return urlPort.startsWith("smb://", ignoreCase = true)
	}

	private fun mapToCloud(username: String, password: String, hostPort: String, domain: String, id: Long?): SmbCloud {
		var builder = SmbCloud //
			.aSmbCloud() //
			.withUrl(hostPort) //
			.withUsername(username) //
			.withPassword(password) //
			.withDomain(domain)

		if (id != null) {
			builder = builder.withId(id)
		}

		return builder.build()
	}

	fun authenticate(username: String, password: String, urlPort: String, domain: String, cloudId: Long?) {
		authenticate(mapToCloud(username, password, urlPort, domain, cloudId))
	}

	private fun authenticate(cloud: SmbCloud) {
		view?.showProgress(ProgressModel(ProgressStateModel.AUTHENTICATION))
		connectToSmbUseCase //
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
		unsubscribeOnDestroy(addOrChangeCloudConnectionUseCase, connectToSmbUseCase)
	}
}
