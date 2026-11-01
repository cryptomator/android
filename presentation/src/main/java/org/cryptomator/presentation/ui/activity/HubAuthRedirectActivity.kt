package org.cryptomator.presentation.ui.activity

import android.app.Activity
import android.os.Bundle
import org.cryptomator.presentation.CryptomatorApp

/**
 * Receives the Hub login's redirect in place of AppAuth's receiver, which can't reach an unlock running in another
 * app's task, e.g. one started from the system file manager or a share.
 */
class HubAuthRedirectActivity : Activity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		(application as CryptomatorApp).component.hubAuthRedirects().receiveRedirect(intent.data)
		finish()
	}
}
