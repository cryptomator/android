package org.cryptomator.presentation.util

import android.net.Uri
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hands the redirect of the Hub login from [org.cryptomator.presentation.ui.activity.HubAuthRedirectActivity] to the
 * unlock waiting for it in this process.
 */
@Singleton
class HubAuthRedirects @Inject constructor() {

	private var redirectUri: Uri? = null
	private var onRedirect: (() -> Unit)? = null

	fun awaitRedirect(onRedirect: () -> Unit) {
		redirectUri = null
		this.onRedirect = onRedirect
	}

	fun stopAwaitingRedirect(onRedirect: () -> Unit) {
		if (this.onRedirect === onRedirect) {
			this.onRedirect = null
		}
	}

	fun receiveRedirect(uri: Uri?) {
		redirectUri = uri
		onRedirect?.invoke()
	}

	fun takeRedirectUri(): Uri? {
		return redirectUri.also {
			redirectUri = null
			onRedirect = null
		}
	}
}
