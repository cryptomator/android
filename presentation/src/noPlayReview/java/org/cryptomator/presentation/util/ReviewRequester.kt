package org.cryptomator.presentation.util

import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity

/**
 * Stub implementation used for flavors that are not distributed via Google Play.
 */
object ReviewRequester {

	fun request(activity: ComponentActivity, onDeferred: () -> Unit) {
		// no-op
	}

	fun storeListingIntent(context: Context): Intent? = null
}
