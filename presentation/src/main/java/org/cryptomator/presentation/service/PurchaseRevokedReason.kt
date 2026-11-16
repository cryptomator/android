package org.cryptomator.presentation.service

import androidx.annotation.StringRes
import org.cryptomator.presentation.R

enum class PurchaseRevokedReason(@StringRes val toastMessageRes: Int) {
	LIFETIME_REFUNDED(R.string.toast_purchase_revoked_refunded),
	SUBSCRIPTION_INACTIVE(R.string.toast_purchase_revoked_subscription_inactive);

	companion object {
		fun fromName(name: String?): PurchaseRevokedReason? {
			return entries.firstOrNull { it.name == name }
		}
	}
}
