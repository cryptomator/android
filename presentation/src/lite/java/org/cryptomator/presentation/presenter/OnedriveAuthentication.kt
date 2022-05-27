package org.cryptomator.presentation.presenter

import android.app.Activity
import org.cryptomator.domain.OnedriveCloud
import org.cryptomator.domain.exception.FatalBackendException

object OnedriveAuthentication {

	fun getAuthenticatedOnedriveCloud(activity: Activity, success: (cloud: OnedriveCloud) -> Unit, failed: (e: FatalBackendException) -> Unit) {
		// no-op
	}

	fun refreshOrCheckAuth(activity: Activity, cloud: OnedriveCloud, success: (cloud: OnedriveCloud) -> Unit, failed: (e: FatalBackendException) -> Unit) {
		// no-op
	}
}


