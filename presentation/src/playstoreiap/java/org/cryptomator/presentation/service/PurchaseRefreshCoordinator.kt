package org.cryptomator.presentation.service

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryPurchasesParams
import timber.log.Timber
import org.cryptomator.presentation.licensing.LicenseEnforcer
import org.cryptomator.util.SharedPreferencesHandler
import java.util.concurrent.atomic.AtomicBoolean

class PurchaseRefreshCoordinator(
	private val sharedPreferencesHandler: SharedPreferencesHandler,
	private val licenseEnforcer: LicenseEnforcer
) {

	// Play Billing async callbacks run on the main thread per BillingClient docs, but we still guard the aggregation
	// counter and the per-query field writes with a shared lock for defensive correctness.
	fun refresh(
		billingClient: BillingClient,
		purchaseManager: PurchaseManager,
		acknowledge: (String) -> Unit,
		onComplete: (RestoreOutcome) -> Unit,
	) {
		val completed = AtomicBoolean(false)
		fun complete(outcome: RestoreOutcome) {
			if (completed.compareAndSet(false, true)) {
				onComplete(outcome)
			}
		}
		try {
			if (!billingClient.isReady) {
				Timber.tag("PurchaseRefreshCoordinator").w("Billing client not ready for refresh")
				complete(RestoreOutcome.FAILED())
				return
			}
			val lock = Any()
			var inappCleared: Boolean? = null
			var subsCleared: Boolean? = null
			var failure: Throwable? = null
			var queriesCompleted = 0
			val totalQueries = 2

			val hadWriteAccessBefore = licenseEnforcer.hasWriteAccess()

			fun onSettled() {
				val localInapp = inappCleared
				val localSubs = subsCleared
				val localFailure = failure
				if (localFailure != null || localInapp == null || localSubs == null) {
					complete(RestoreOutcome.FAILED(localFailure))
					return
				}
				val hadWriteAccessAfter = licenseEnforcer.hasWriteAccess()
				if (hadWriteAccessBefore && !hadWriteAccessAfter && (localInapp || localSubs)) {
					val reason = if (localInapp) {
						PurchaseRevokedReason.LIFETIME_REFUNDED
					} else {
						PurchaseRevokedReason.SUBSCRIPTION_INACTIVE
					}
					sharedPreferencesHandler.setPurchaseRevokedState(pending = true, reason = reason.name)
				}
				val hasActivePurchase = sharedPreferencesHandler.licenseToken().isNotEmpty() || sharedPreferencesHandler.hasRunningSubscription()
				complete(if (hasActivePurchase) RestoreOutcome.RESTORED else RestoreOutcome.NOTHING_TO_RESTORE)
			}

			fun onQueryComplete() {
				val ready: Boolean
				synchronized(lock) {
					queriesCompleted++
					ready = queriesCompleted == totalQueries
				}
				if (ready) {
					onSettled()
				}
			}

			fun query(params: QueryPurchasesParams, handleAndSet: (List<Purchase>) -> Unit) {
				billingClient.queryPurchasesAsync(params) { billingResult: BillingResult, purchases: List<Purchase> ->
					synchronized(lock) {
						if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
							failure = Throwable("Query failed with code: ${billingResult.responseCode}")
						} else {
							handleAndSet(purchases)
						}
					}
					onQueryComplete()
				}
			}

			query(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) {
				inappCleared = purchaseManager.handlePurchases(PurchaseManager.Kind.LIFETIME, it, clearIfNotFound = true, acknowledgePurchase = acknowledge)
			}
			query(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()) {
				subsCleared = purchaseManager.handlePurchases(PurchaseManager.Kind.SUBSCRIPTION, it, clearIfNotFound = true, acknowledgePurchase = acknowledge)
			}
		} catch (e: Throwable) {
			Timber.tag("PurchaseRefreshCoordinator").e(e, "Unexpected error during purchase refresh")
			complete(RestoreOutcome.FAILED(e))
		}
	}
}
