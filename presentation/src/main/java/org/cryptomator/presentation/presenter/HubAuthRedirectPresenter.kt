package org.cryptomator.presentation.presenter

import android.net.Uri
import org.cryptomator.domain.di.PerView
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.ui.activity.view.HubAuthRedirectView
import org.cryptomator.presentation.util.HubAuthRedirects
import javax.inject.Inject

@PerView
class HubAuthRedirectPresenter @Inject constructor(
	exceptionHandlers: ExceptionHandlers, //
	private val hubAuthRedirects: HubAuthRedirects, //
) : Presenter<HubAuthRedirectView>(exceptionHandlers) {

	fun onRedirect(redirectUri: Uri?) {
		hubAuthRedirects.receiveRedirect(redirectUri)
		finish()
	}
}
