package org.cryptomator.presentation.presenter

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.di.PerView
import org.cryptomator.domain.exception.authentication.AuthenticationException
import org.cryptomator.generator.Callback
import org.cryptomator.presentation.CryptomatorApp
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.ui.activity.view.AutoUploadRefreshTokenView
import org.cryptomator.presentation.workflow.ActivityResult
import org.cryptomator.presentation.workflow.AuthenticationExceptionHandler
import javax.inject.Inject

@PerView
class AutoUploadRefreshTokenPresenter @Inject constructor(
	exceptionHandlers: ExceptionHandlers,  //
	private val authenticationExceptionHandler: AuthenticationExceptionHandler,  //
) : Presenter<AutoUploadRefreshTokenView>(exceptionHandlers) {

	fun refreshCloudToken(authenticationException: AuthenticationException) {
		authenticationExceptionHandler.handleAuthenticationException( //
			this@AutoUploadRefreshTokenPresenter,  //
			authenticationException,  //
			ActivityResultCallbacks.onAutoUploadCloudAuthenticated(authenticationException.cloud)
		)
	}

	@Callback(dispatchResultOkOnly = false)
	fun onAutoUploadCloudAuthenticated(result: ActivityResult, cloud: Cloud) {
		if (result.isResultOk) {
			val cryptomatorApp = activity().application as CryptomatorApp
			cryptomatorApp.startAutoUpload(cloud)
		}
		finish()
	}
}
