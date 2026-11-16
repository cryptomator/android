package org.cryptomator.presentation.service

import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import java.lang.ref.WeakReference
import timber.log.Timber

/**
 * Stub implementation used for flavors that do not bundle Google Play Billing.
 */
class IapBillingService : Service() {

	override fun onCreate() {
		super.onCreate()
		Timber.tag("IapBillingService").d("Stub service created")
	}

	override fun onBind(intent: Intent?): IBinder = Binder()

	class Binder : android.os.Binder() {

		fun init(context: Context) {
			// no-op
		}

		fun startPurchaseFlow(activity: WeakReference<Activity>, productId: String) {
			// no-op
		}

		fun queryProductDetails(callback: (List<ProductInfo>) -> Unit) {
			callback(emptyList())
		}

		fun restorePurchases(onComplete: (RestoreOutcome) -> Unit) {
			onComplete(RestoreOutcome.NOTHING_TO_RESTORE)
		}
	}
}
