package org.cryptomator.presentation.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.android.play.core.review.ReviewManagerFactory
import timber.log.Timber

object ReviewRequester {

	/**
	 * [onReady] receives the launch of the review card once Play has answered. Play decides whether the card is shown and
	 * does not report the outcome. A failed request is not retried.
	 */
	fun request(activity: Activity, onReady: (launch: () -> Unit) -> Unit) {
		val reviewManager = ReviewManagerFactory.create(activity)
		reviewManager.requestReviewFlow().addOnCompleteListener { task ->
			if (task.isSuccessful) {
				onReady {
					Timber.tag("ReviewRequester").i("Launching the review flow")
					reviewManager.launchReviewFlow(activity, task.result)
				}
			} else {
				Timber.tag("ReviewRequester").w(task.exception, "Requesting the review flow failed")
			}
		}
	}

	fun storeListingIntent(context: Context): Intent? {
		return Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + context.packageName))
	}
}
