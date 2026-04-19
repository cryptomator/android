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
	private val licenseEnforcer: LicenseEnforcer = LicenseEnforcer(sharedPreferencesHandler)
) {

	// Play Billing async callbacks run on the main thread per BillingClient docs, but we still guard the aggregation
	/**
	 * Orchestrates a refresh of Play Store purchases for both INAPP and SUBS, aggregates their results,
	 * and invokes a single completion callback with the overall restore outcome.
	 *
	 * Performs two asynchronous queries (INAPP and SUBS), processes results via the provided
	 * PurchaseManager, and ensures `onComplete` is called at most once with:
	 * - `RestoreOutcome.RESTORED` if either product set indicates restored purchases,
	 * - `RestoreOutcome.NOTHING_TO_RESTORE` if no changes were found,
	 * - `RestoreOutcome.FAILED` on any query or unexpected error.
	 *
	 * If write access to license state was lost during the refresh and a product set was cleared,
	 * a pending purchase-revoked state is recorded in shared preferences with an appropriate reason.
	 *
	 * @param billingClient Play Billing client used to query purchases.
	 * @param purchaseManager Handles processing of the queried INAPP and SUBS purchase lists.
	 * @param acknowledge Callback invoked with a purchase token to acknowledge a purchase when required.
	 * @param onComplete Callback invoked once with the aggregated `RestoreOutcome`.
	 */
	fun refresh(
		billingClient: BillingClient,
		purchaseManager: PurchaseManager,
		acknowledge: (String) -> Unit,
		onComplete: (RestoreOutcome) -> Unit,
	) {
		val completed = AtomicBoolean(false)
		/**
		 * Invokes the final completion callback exactly once with the provided outcome.
		 *
		 * @param outcome The restore outcome to deliver to the `onComplete` callback.
		 */
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
			var inappChange: PurchaseFieldChange? = null
			var subsChange: PurchaseFieldChange? = null
			var failure: Throwable? = null
			var queriesCompleted = 0
			val totalQueries = 2

			val hadWriteAccessBefore = licenseEnforcer.hasWriteAccess()

			/**
			 * Finalizes the two purchase queries, determines the final restore outcome, and updates revoked state when applicable.
			 *
			 * If a failure was recorded or either purchase result is missing, completes with `RestoreOutcome.FAILED`.
			 * If write access was present before but is now absent and either purchase set was cleared, marks a pending purchase-revoked state with an appropriate `PurchaseRevokedReason`.
			 * Otherwise, completes with `RestoreOutcome.RESTORED` if either purchase result indicates a restored item, or `RestoreOutcome.NOTHING_TO_RESTORE` if not.
			 */
			fun onSettled() {
				val localInapp = inappChange
				val localSubs = subsChange
				val localFailure = failure
				if (localFailure != null || localInapp == null || localSubs == null) {
					complete(RestoreOutcome.FAILED(localFailure))
					return
				}
				val hadWriteAccessAfter = licenseEnforcer.hasWriteAccess()
				if (hadWriteAccessBefore && !hadWriteAccessAfter && (localInapp.cleared || localSubs.cleared)) {
					val reason = if (localInapp.cleared) {
						PurchaseRevokedReason.LIFETIME_REFUNDED
					} else {
						PurchaseRevokedReason.SUBSCRIPTION_INACTIVE
					}
					sharedPreferencesHandler.setPurchaseRevokedState(pending = true, reason = reason.name)
				}
				val outcome = if (localInapp.after || localSubs.after) {
					RestoreOutcome.RESTORED
				} else {
					RestoreOutcome.NOTHING_TO_RESTORE
				}
				complete(outcome)
			}

			/**
			 * Records completion of a single purchase query and, when all queries have finished, invokes settlement.
			 *
			 * Increments the shared completed-query counter under the coordinator lock and calls `onSettled()` once
			 * the number of completed queries equals the expected total.
			 */
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

			val inappParams = QueryPurchasesParams.newBuilder()
				.setProductType(BillingClient.ProductType.INAPP)
				.build()
			billingClient.queryPurchasesAsync(inappParams) { billingResult: BillingResult, purchases: List<Purchase> ->
				synchronized(lock) {
					if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
						failure = Throwable("Query failed with code: ${billingResult.responseCode}")
					} else {
						inappChange = purchaseManager.handleInAppPurchases(purchases, clearIfNotFound = true, acknowledgePurchase = acknowledge)
					}
				}
				onQueryComplete()
			}
			val subsParams = QueryPurchasesParams.newBuilder()
				.setProductType(BillingClient.ProductType.SUBS)
				.build()
			billingClient.queryPurchasesAsync(subsParams) { billingResult: BillingResult, purchases: List<Purchase> ->
				synchronized(lock) {
					if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
						failure = Throwable("Query failed with code: ${billingResult.responseCode}")
					} else {
						subsChange = purchaseManager.handleSubscriptionPurchases(purchases, clearIfNotFound = true, acknowledgePurchase = acknowledge)
					}
				}
				onQueryComplete()
			}
		} catch (e: Throwable) {
			Timber.tag("PurchaseRefreshCoordinator").e(e, "Unexpected error during purchase refresh")
			complete(RestoreOutcome.FAILED(e))
		}
	}
}
