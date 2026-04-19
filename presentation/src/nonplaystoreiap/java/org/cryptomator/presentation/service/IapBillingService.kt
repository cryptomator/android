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

	/**
	 * Performs base Service initialization and logs a debug message indicating the stub billing service was created.
	 */
	override fun onCreate() {
		super.onCreate()
		Timber.tag("IapBillingService").d("Stub service created")
	}

	/**
 * Provides a binder exposing the stubbed in-app billing API to clients.
 *
 * @param intent The intent used to bind to the service; its contents are ignored.
 * @return A new `Binder` instance that exposes no-op billing methods and returns empty/default results.
 */
override fun onBind(intent: Intent?): IBinder = Binder()

	class Binder : android.os.Binder() {

		/**
		 * Performs initialization for the billing binder used in app flavors without Google Play Billing.
		 *
		 * This implementation intentionally does nothing; the provided `context` is ignored.
		 *
		 * @param context Android `Context` that is accepted for API compatibility but not used.
		 */
		fun init(context: Context) {
			// no-op
		}

		/**
		 * Placeholder for initiating an in-app purchase; in this stub implementation it performs no action.
		 *
		 * @param activity WeakReference to the calling Activity; ignored by this implementation.
		 * @param productId The ID of the product to purchase; ignored by this implementation.
		 */
		fun startPurchaseFlow(activity: WeakReference<Activity>, productId: String) {
			// no-op
		}

		/**
		 * Immediately invokes the provided callback with an empty list of product details.
		 *
		 * @param callback Called with the available `ProductInfo` items; in this stub implementation it is invoked with an empty `List`.
		 */
		fun queryProductDetails(callback: (List<ProductInfo>) -> Unit) {
			callback(emptyList())
		}

		/**
		 * Notifies the caller that there are no purchases to restore and invokes the provided completion callback.
		 *
		 * @param onComplete Callback that receives the restore outcome; invoked with `RestoreOutcome.NOTHING_TO_RESTORE`.
		 */
		fun restorePurchases(onComplete: (RestoreOutcome) -> Unit) {
			onComplete(RestoreOutcome.NOTHING_TO_RESTORE)
		}
	}
}
