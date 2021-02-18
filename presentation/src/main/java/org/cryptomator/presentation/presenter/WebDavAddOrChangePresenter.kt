package org.cryptomator.presentation.presenter

import android.widget.Toast
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.WebDavCloud
import org.cryptomator.domain.di.PerView
import org.cryptomator.domain.usecases.cloud.AddOrChangeCloudConnectionUseCase
import org.cryptomator.domain.usecases.cloud.ConnectToWebDavUseCase
import org.cryptomator.generator.Callback
import org.cryptomator.presentation.R
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.model.CloudModel
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.model.ProgressStateModel
import org.cryptomator.presentation.ui.activity.view.WebDavAddOrChangeView
import org.cryptomator.presentation.workflow.ActivityResult
import org.cryptomator.presentation.workflow.AuthenticationExceptionHandler
import org.cryptomator.util.crypto.CredentialCryptor
import javax.inject.Inject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@PerView
class WebDavAddOrChangePresenter @Inject internal constructor( //
		private val addOrChangeCloudConnectionUseCase: AddOrChangeCloudConnectionUseCase,  //
		private val connectToWebDavUseCase: ConnectToWebDavUseCase,  //
		private val authenticationExceptionHandler: AuthenticationExceptionHandler,  //
		exceptionMappings: ExceptionHandlers) : Presenter<WebDavAddOrChangeView>(exceptionMappings) {

	fun checkUserInput(urlPort: String, username: String, password: String, cloudId: Long?, certificate: String?) {
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
			if (cloudId == null && urlPortWithoutTrailingSlash[4] != 's') {
				view?.showAskForHttpDialog(urlPortWithoutTrailingSlash, username, encryptedPassword, cloudId, certificate)
			} else {
				view?.onCheckUserInputSucceeded(urlPortWithoutTrailingSlash, username, encryptedPassword, cloudId, certificate)
			}
		}
	}

	private fun encryptPassword(password: String): String {
		return CredentialCryptor //
				.getInstance(context()) //
				.encrypt(password)
	}

	private fun isValid(urlPort: String): Boolean {
		return urlPort.toHttpUrlOrNull() != null
	}

	private fun mapToCloud(username: String, password: String, hostPort: String, id: Long?, certificate: String?): WebDavCloud {
		var builder = WebDavCloud //
				.aWebDavCloudCloud() //
				.withUrl(hostPort) //
				.withUsername(username) //
				.withPassword(password)

		if (id != null) {
			builder = builder.withId(id)
		}

		if (certificate != null) {
			builder = builder.withCertificate(certificate)
		}

		return builder.build()
	}

	fun authenticate(username: String, password: String, urlPort: String, cloudId: Long?, certificate: String?) {
		authenticate(mapToCloud(username, password, urlPort, cloudId, certificate))
	}

	private fun authenticate(cloud: WebDavCloud) {
		view?.showProgress(ProgressModel(ProgressStateModel.AUTHENTICATION))
		connectToWebDavUseCase //
				.withCloud(cloud) //
				.run(object : DefaultResultHandler<Void?>() {
					override fun onSuccess(void: Void?) {
						onCloudAuthenticated(cloud)
					}

					override fun onError(e: Throwable) {
						view?.showProgress(ProgressModel.COMPLETED)
						if (!authenticationExceptionHandler.handleAuthenticationException(this@WebDavAddOrChangePresenter, e, ActivityResultCallbacks.handledAuthenticationWebDavCloud())) {
							super.onError(e)
						}
					}
				})
	}

	@Callback
	fun handledAuthenticationWebDavCloud(result: ActivityResult) {
		if (result.intent().extras?.getBoolean(AuthenticateCloudPresenter.WEBDAV_ACCEPTED_UNTRUSTED_CERTIFICATE, false) == true) {
			authenticate((result.singleResult as CloudModel).toCloud() as WebDavCloud)
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
		unsubscribeOnDestroy(addOrChangeCloudConnectionUseCase, connectToWebDavUseCase)
	}
}
