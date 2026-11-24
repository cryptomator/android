package org.cryptomator.presentation.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import com.google.android.play.core.review.ReviewManagerFactory
import timber.log.Timber

object ReviewRequester {

	/**
	 * Play decides whether the review card is shown and does not report the outcome. If Play answers after [activity] has
	 * left the resumed state, the card is not launched and [onDeferred] is called instead. A failed request is not retried.
	 */
	fun request(activity: ComponentActivity, onDeferred: () -> Unit) {
		val reviewManager = ReviewManagerFactory.create(activity)
		reviewManager.requestReviewFlow().addOnCompleteListener { task ->
			if (!task.isSuccessful) {
				Timber.tag("ReviewRequester").w(task.exception, "Requesting the review flow failed")
			} else if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
				Timber.tag("ReviewRequester").i("Activity is not resumed, deferring the review flow")
				onDeferred()
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
