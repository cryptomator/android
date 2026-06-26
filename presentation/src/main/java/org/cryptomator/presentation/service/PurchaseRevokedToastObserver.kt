package org.cryptomator.presentation.service

import android.app.Activity
import android.widget.Toast
import org.cryptomator.util.NoOpActivityLifecycleCallbacks
import org.cryptomator.util.SharedPreferencesHandler
import timber.log.Timber

class PurchaseRevokedToastObserver(
	private val sharedPreferencesHandler: SharedPreferencesHandler
) : NoOpActivityLifecycleCallbacks() {

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
