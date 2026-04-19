package org.cryptomator.presentation.service

import android.app.Activity
import android.widget.Toast
import org.cryptomator.util.NoOpActivityLifecycleCallbacks
import org.cryptomator.util.SharedPreferencesHandler
import timber.log.Timber

class PurchaseRevokedToastObserver(
	private val sharedPreferencesHandler: SharedPreferencesHandler
) : NoOpActivityLifecycleCallbacks() {

	/**
	 * Checks for a pending "purchase revoked" flag when an activity resumes, shows a corresponding Toast if a valid reason is stored, logs a warning if the reason is invalid or missing, and clears the pending state.
	 *
	 * @param activity The resumed Activity used to display the Toast when a valid revoke reason is found.
	 */
	override fun onActivityResumed(activity: Activity) {
		if (!sharedPreferencesHandler.purchaseRevokedPending()) {
			return
		}
		val reason = PurchaseRevokedReason.fromName(sharedPreferencesHandler.purchaseRevokedReason())
		if (reason != null) {
			Toast.makeText(activity, reason.toastMessageRes, Toast.LENGTH_LONG).show()
		} else {
			Timber.tag("PurchaseRevokedToastObserver").w("Invalid or missing revoke reason; clearing flag")
		}
		sharedPreferencesHandler.clearPurchaseRevokedState()
	}
}
