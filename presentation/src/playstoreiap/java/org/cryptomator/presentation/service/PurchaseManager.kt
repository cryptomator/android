package org.cryptomator.presentation.service

import com.android.billingclient.api.Purchase
import org.cryptomator.util.SharedPreferencesHandler
import timber.log.Timber

class PurchaseManager(
	private val sharedPreferencesHandler: SharedPreferencesHandler
) {

	enum class Kind(val productId: String) {
		LIFETIME(ProductInfo.PRODUCT_FULL_VERSION),
		SUBSCRIPTION(ProductInfo.PRODUCT_YEARLY_SUBSCRIPTION)
	}

	/** Returns true iff this call actively cleared the field from a previously active state. */
	fun handlePurchases(
		kind: Kind,
		purchases: List<Purchase>,
		clearIfNotFound: Boolean = false,
		acknowledgePurchase: (String) -> Unit
	): Boolean {
		val wasActive = when (kind) {
			Kind.LIFETIME -> sharedPreferencesHandler.licenseToken().isNotEmpty()
			Kind.SUBSCRIPTION -> sharedPreferencesHandler.hasRunningSubscription()
		}
		for (purchase in purchases) {
			if (!purchase.products.contains(kind.productId)) continue
			when (purchase.purchaseState) {
				Purchase.PurchaseState.PENDING -> {
					Timber.tag("PurchaseManager").d("Purchase pending for %s, skipping", kind.productId)
					continue
				}
				Purchase.PurchaseState.PURCHASED -> {
					Timber.tag("PurchaseManager").d("Purchase found for %s: %s", kind.productId, purchase.signature)
					applyActive(kind, purchase, wasActive)
					if (!purchase.isAcknowledged) {
						acknowledgePurchase(purchase.purchaseToken)
					}
					return false
				}
			}
		}
		return clearIfNotFound && applyCleared(kind, wasActive)
	}

	private fun applyActive(kind: Kind, purchase: Purchase, wasActive: Boolean) {
		when (kind) {
			Kind.LIFETIME -> if (!wasActive) {
				sharedPreferencesHandler.setLicenseToken(purchase.purchaseToken)
			}
			Kind.SUBSCRIPTION -> sharedPreferencesHandler.setHasRunningSubscription(true)
		}
	}

	private fun applyCleared(kind: Kind, wasActive: Boolean): Boolean = when (kind) {
		Kind.LIFETIME -> if (wasActive) {
			Timber.tag("PurchaseManager").i("Remove license, purchase does not exist anymore")
			sharedPreferencesHandler.setLicenseToken("")
			true
		} else false
		Kind.SUBSCRIPTION -> {
			sharedPreferencesHandler.setHasRunningSubscription(false)
			wasActive
		}
	}
}
