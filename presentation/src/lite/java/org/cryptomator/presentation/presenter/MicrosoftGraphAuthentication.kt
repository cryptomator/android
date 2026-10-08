package org.cryptomator.presentation.presenter

import android.app.Activity
import org.cryptomator.domain.MicrosoftGraphCloud
import org.cryptomator.domain.OnedriveCloud
import org.cryptomator.domain.SharepointCloud
import org.cryptomator.domain.exception.FatalBackendException

object MicrosoftGraphAuthentication {

	fun getAuthenticatedOnedriveCloud(activity: Activity, success: (cloud: OnedriveCloud) -> Unit, failed: (e: FatalBackendException) -> Unit) {
		// no-op
	}

	fun getAuthenticatedSharepointCloud(activity: Activity, siteUrl: String, success: (cloud: SharepointCloud) -> Unit, failed: (e: FatalBackendException) -> Unit) {
		// no-op
	}

	fun refreshOrCheckAuth(activity: Activity, cloud: MicrosoftGraphCloud, success: (cloud: MicrosoftGraphCloud) -> Unit, failed: (e: FatalBackendException) -> Unit) {
		// no-op
	}
}
