package org.cryptomator.presentation.util

import android.app.Activity
import android.content.Context
import android.content.Intent

/**
 * Stub implementation used for flavors that are not distributed via Google Play.
 */
object ReviewRequester {

	fun request(activity: Activity) {
		// no-op
	}

	fun storeListingIntent(context: Context): Intent? = null
}
