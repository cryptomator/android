package org.cryptomator.presentation.service

import com.android.billingclient.api.Purchase
import org.cryptomator.util.SharedPreferencesHandler
import timber.log.Timber

class PurchaseManager(
	private val sharedPreferencesHandler: SharedPreferencesHandler
) {

	/**
	 * Updates the persisted license token based on the provided in-app purchases for the full-version product.
	 *
	 * Iterates the purchases and, on finding a `PURCHASED` entry for the full-version product, persists the purchase token
	 * if no token existed before and invokes the `acknowledgePurchase` callback for unacknowledged purchases.
	 * If no matching `PURCHASED` purchase is found and `clearIfNotFound` is true, clears the stored license token.
	 *
	 * @param purchases The list of Play Billing `Purchase` objects to inspect.
	 * @param clearIfNotFound If true, clear the stored license token when no matching purchased full-version is found.
	 * @param acknowledgePurchase Callback invoked with a purchase token to acknowledge an unacknowledged purchase.
	 * @return A `PurchaseFieldChange` describing the license state before and after processing and whether the token was cleared.
	 */
	fun handleInAppPurchases(purchases: List<Purchase>, clearIfNotFound: Boolean = false, acknowledgePurchase: (String) -> Unit): PurchaseFieldChange {
		val tokenBefore = sharedPreferencesHandler.licenseToken()
		val before = tokenBefore.isNotEmpty()
		for (purchase in purchases) {
			if (!purchase.products.contains(ProductInfo.PRODUCT_FULL_VERSION)) {
				continue
			}
			if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
				Timber.tag("PurchaseManager").d("In-app purchase pending, skipping")
				continue
			}
			if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
				Timber.tag("PurchaseManager").d("In-app purchase found: %s", purchase.signature)
				if (tokenBefore.isEmpty()) {
					sharedPreferencesHandler.setLicenseToken(purchase.purchaseToken)
				}
				if (!purchase.isAcknowledged) {
					acknowledgePurchase(purchase.purchaseToken)
				}
				return PurchaseFieldChange(before = before, after = true, cleared = false)
			}
		}
		if (clearIfNotFound && before) {
			Timber.tag("PurchaseManager").i("Remove license, purchase does not exist anymore")
			sharedPreferencesHandler.setLicenseToken("")
			return PurchaseFieldChange(before = true, after = false, cleared = true)
		}
		return PurchaseFieldChange(before = before, after = before, cleared = false)
	}

	/**
	 * Updates persisted subscription state based on the provided Google Play purchases.
	 *
	 * Sets the stored running-subscription flag to true when a `PURCHASED` purchase for
	 * `ProductInfo.PRODUCT_YEARLY_SUBSCRIPTION` is found (and invokes the provided acknowledgement
	 * callback for unacknowledged purchases). If no matching `PURCHASED` purchase is found and
	 * `clearIfNotFound` is true, clears the stored running-subscription flag.
	 *
	 * @param purchases The list of Google Play `Purchase` objects to inspect.
	 * @param clearIfNotFound If true, clears the stored subscription state when no matching purchase is found.
	 * @param acknowledgePurchase Callback invoked with a purchase token to acknowledge an unacknowledged purchase.
	 * @return A `PurchaseFieldChange` describing the subscription state before and after processing and whether it was cleared.
	 */
	fun handleSubscriptionPurchases(purchases: List<Purchase>, clearIfNotFound: Boolean = false, acknowledgePurchase: (String) -> Unit): PurchaseFieldChange {
		val before = sharedPreferencesHandler.hasRunningSubscription()
		for (purchase in purchases) {
			if (!purchase.products.contains(ProductInfo.PRODUCT_YEARLY_SUBSCRIPTION)) {
				continue
			}
			if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
				Timber.tag("PurchaseManager").d("Subscription purchase pending, skipping")
				continue
			}
			if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
				Timber.tag("PurchaseManager").d("Subscription found: %s", purchase.signature)
				sharedPreferencesHandler.setHasRunningSubscription(true)
				if (!purchase.isAcknowledged) {
					acknowledgePurchase(purchase.purchaseToken)
				}
				return PurchaseFieldChange(before = before, after = true, cleared = false)
			}
		}
		if (clearIfNotFound) {
			sharedPreferencesHandler.setHasRunningSubscription(false)
			return PurchaseFieldChange(before = before, after = false, cleared = before)
		}
		return PurchaseFieldChange(before = before, after = before, cleared = false)
	}
}
