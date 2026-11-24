package org.cryptomator.presentation.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.android.play.core.review.ReviewManagerFactory
import timber.log.Timber

object ReviewRequester {

	/** Play decides whether the review card is shown and does not report the outcome. */
	fun request(activity: Activity) {
		val reviewManager = ReviewManagerFactory.create(activity)
		reviewManager.requestReviewFlow().addOnCompleteListener { task ->
			if (!task.isSuccessful) {
				Timber.tag("ReviewRequester").w(task.exception, "Requesting the review flow failed")
			} else if (activity.isFinishing || activity.isDestroyed) {
				Timber.tag("ReviewRequester").i("Activity is gone, skipping the review flow")
			} else {
				Timber.tag("ReviewRequester").i("Launching the review flow")
				reviewManager.launchReviewFlow(activity, task.result)
			}
		}
	}

	fun storeListingIntent(context: Context): Intent? {
		return Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + context.packageName))
	}
}
