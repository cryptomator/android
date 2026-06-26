package org.cryptomator.presentation.ui.layout

import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import android.view.View
import org.cryptomator.presentation.CryptomatorApp
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.ViewLicenseCheckContentBinding
import org.cryptomator.presentation.licensing.LicenseEnforcer
import org.cryptomator.presentation.service.ProductInfo
import org.cryptomator.presentation.service.ProductPrices
import org.cryptomator.presentation.service.RestoreOutcome
import org.cryptomator.presentation.service.resolveProductPrices
import org.cryptomator.presentation.service.toDialogFragment
import org.cryptomator.presentation.ui.activity.BaseActivity
import java.lang.ref.WeakReference
import java.text.DateFormat
import java.util.Date

/** Shared visibility-toggling logic for the license check content included layout. */
class LicenseContentViewBinder(
	private val binding: ViewLicenseCheckContentBinding,
	private val isFreemiumFlavor: Boolean
) {

	private val context get() = binding.root.context

	/** Sets the initial visibility state and button defaults for IAP mode. */
	fun bindInitialIapLayout() {
		binding.licenseEntryGroup.visibility = View.GONE
		binding.btnPurchase.visibility = View.GONE
		binding.tvLicenseLink.visibility = View.GONE
		binding.purchaseOptionsGroup.visibility = View.VISIBLE
		binding.tvRestorePurchase.visibility = View.VISIBLE
		binding.legalLinksGroup.visibility = View.VISIBLE
		binding.rowSubscription.isEnabled = false
		binding.btnSubscription.isEnabled = false
		binding.rowLifetime.isEnabled = false
		binding.btnLifetime.isEnabled = false
	}

	/** Sets the initial visibility state for license-entry (non-IAP) mode. */
	fun bindInitialLicenseEntryLayout() {
		binding.licenseEntryGroup.visibility = View.VISIBLE
		binding.purchaseOptionsGroup.visibility = View.GONE
		binding.tvRestorePurchase.visibility = View.GONE
		binding.legalLinksGroup.visibility = View.GONE
		binding.tvLicenseLink.visibility = View.VISIBLE
		binding.tvLicenseLink.text = context.getString(R.string.dialog_enter_license_content)
	}

	/** Sets the initial visibility state for license-entry mode with the trial row visible (welcome flow only). */
	fun bindInitialLicenseEntryWithTrialLayout() {
		binding.licenseEntryGroup.visibility = View.GONE
		binding.btnPurchase.visibility = View.GONE
		binding.purchaseOptionsGroup.visibility = View.VISIBLE
		binding.rowSubscription.visibility = View.GONE
		binding.rowLifetime.visibility = View.GONE
		binding.dividerTrialSubscription.visibility = View.GONE
		binding.dividerSubscriptionLifetime.visibility = View.GONE
		binding.rowTrial.visibility = View.VISIBLE
		binding.dividerTrialEnterLicense.visibility = View.VISIBLE
		binding.rowEnterLicense.visibility = View.VISIBLE
		binding.tvRestorePurchase.visibility = View.GONE
		binding.legalLinksGroup.visibility = View.GONE
		binding.tvLicenseLink.visibility = View.GONE
	}

	/** Wires the Enter License row click listener (welcome non-IAP flow only). */
	fun bindEnterLicenseButton(onEnterLicenseClicked: () -> Unit) {
		binding.rowEnterLicense.setOnClickListener { onEnterLicenseClicked() }
	}

	/** Sets click listeners on Terms and Privacy links. */
	fun bindLegalLinks() {
		binding.tvTerms.setOnClickListener {
			context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://cryptomator.org/terms/")))
		}
		binding.tvPrivacy.setOnClickListener {
			context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://cryptomator.org/privacy/")))
		}
	}

	/** Wires trial, subscription, lifetime, and restore button click listeners. */
	fun bindPurchaseButtons(
		activity: BaseActivity<*>,
		app: CryptomatorApp,
		onTrialClicked: () -> Unit
	) {
		binding.btnTrial.setOnClickListener { onTrialClicked() }
		binding.rowSubscription.setOnClickListener {
			app.launchPurchaseFlow(WeakReference(activity), ProductInfo.PRODUCT_YEARLY_SUBSCRIPTION)
		}
		binding.rowLifetime.setOnClickListener {
			app.launchPurchaseFlow(WeakReference(activity), ProductInfo.PRODUCT_FULL_VERSION)
		}
		binding.tvRestorePurchase.setOnClickListener {
			binding.tvRestorePurchase.isEnabled = false
			app.restorePurchases { outcome ->
				binding.root.post {
					binding.tvRestorePurchase.isEnabled = true
					showRestoreOutcome(activity, outcome)
				}
			}
		}
	}

	private fun showRestoreOutcome(activity: BaseActivity<*>, outcome: RestoreOutcome) {
		activity.showDialog(outcome.toDialogFragment())
	}

	/** Queries product details and updates price buttons on the UI thread. */
	fun loadAndBindPrices(app: CryptomatorApp) {
		app.queryProductDetails { products ->
			val prices = products.resolveProductPrices()
			binding.root.post { bindProductPrices(prices) }
		}
	}

	/** Updates subscription and lifetime button text and enabled state from resolved prices. */
	fun bindProductPrices(prices: ProductPrices) {
		if (!prices.subscriptionPrice.isNullOrEmpty()) {
			binding.btnSubscription.text = prices.subscriptionPrice
			binding.rowSubscription.isEnabled = true
			binding.btnSubscription.isEnabled = true
		}
		if (!prices.lifetimePrice.isNullOrEmpty()) {
			binding.btnLifetime.text = prices.lifetimeDiscountPrice ?: prices.lifetimePrice
			binding.rowLifetime.isEnabled = true
			binding.btnLifetime.isEnabled = true
		}
		if (prices.lifetimeDiscountPrice != null) {
			binding.tvLifetimePrice.text = prices.lifetimePrice
			binding.tvLifetimePrice.paintFlags = binding.tvLifetimePrice.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
			binding.tvLifetimePrice.visibility = View.VISIBLE
			binding.tvLifetimeDiscountSubline.text = lifetimeDiscountSubline(prices)
			binding.tvLifetimeDiscountSubline.visibility = View.VISIBLE
		} else {
			binding.tvLifetimePrice.visibility = View.GONE
			binding.tvLifetimeDiscountSubline.visibility = View.GONE
		}
	}

	private fun lifetimeDiscountSubline(prices: ProductPrices): String {
		val percent = prices.lifetimeDiscountPercent
		val endTimeMillis = prices.lifetimeDiscountEndTimeMillis
		if (percent == null || endTimeMillis == null) {
			return context.getString(R.string.screen_license_check_lifetime_discount_badge_generic)
		}
		val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(endTimeMillis))
		return context.getString(R.string.screen_license_check_lifetime_discount_badge_until, percent, date)
	}

	/** Refreshes purchase/trial visibility and the header info text from the current license state. */
	fun bindState(uiState: LicenseEnforcer.LicenseUiState, lockedAction: LicenseEnforcer.LockedAction? = null) {
		bindPurchaseVisibility(uiState)
		bindTrialVisibility(uiState.trialState)
		bindInfoText(uiState, lockedAction)
	}

	private fun bindPurchaseVisibility(uiState: LicenseEnforcer.LicenseUiState) {
		if (isFreemiumFlavor) {
			when {
				uiState.hasLifetimeLicense -> {
					binding.purchaseOptionsGroup.visibility = View.GONE
					binding.tvRestorePurchase.visibility = View.GONE
					binding.tvTrialStatusBadge.visibility = View.GONE
					binding.tvTrialExpiration.visibility = View.GONE
				}
				uiState.hasRunningSubscription -> {
					binding.purchaseOptionsGroup.visibility = View.VISIBLE
					binding.tvRestorePurchase.visibility = View.GONE
					binding.rowTrial.visibility = View.GONE
					binding.dividerTrialSubscription.visibility = View.GONE
					binding.rowSubscription.visibility = View.GONE
					binding.dividerSubscriptionLifetime.visibility = View.GONE
				}
				else -> {
					binding.purchaseOptionsGroup.visibility = View.VISIBLE
					binding.tvRestorePurchase.visibility = View.VISIBLE
				}
			}
		} else {
			binding.btnPurchase.isEnabled = !uiState.hasWriteAccess
			if (uiState.hasPaidLicense) {
				binding.rowTrial.visibility = View.GONE
				binding.dividerTrialEnterLicense.visibility = View.GONE
				binding.rowEnterLicense.visibility = View.GONE
			}
		}
	}

	private fun bindTrialVisibility(trialState: LicenseEnforcer.TrialState) {
		if (trialState.isActive || trialState.isExpired) {
			binding.trialButtonGroup.visibility = View.GONE
			binding.tvTrialStatusBadge.visibility = View.VISIBLE
			binding.tvTrialStatusBadge.text = context.getString(
				if (trialState.isActive) R.string.screen_license_check_trial_status_active
				else R.string.screen_license_check_trial_status_expired
			)
			binding.tvTrialExpiration.visibility = View.VISIBLE
			binding.tvTrialExpiration.text = trialState.formattedExpirationDate?.let {
				context.getString(R.string.screen_license_check_trial_expiration, it)
			}
		} else {
			binding.trialButtonGroup.visibility = View.VISIBLE
			binding.tvTrialStatusBadge.visibility = View.GONE
			binding.tvTrialExpiration.visibility = View.GONE
		}
	}

	private fun bindInfoText(uiState: LicenseEnforcer.LicenseUiState, lockedAction: LicenseEnforcer.LockedAction?) {
		val text: String? = when {
			lockedAction != null -> context.getString(lockedAction.headerMessageRes)
			uiState.hasSubscriptionUpgradeHint -> context.getString(R.string.screen_license_check_subscription_upgrade_hint)
			uiState.hasCancelSubscriptionHint -> context.getString(R.string.screen_license_check_cancel_subscription_hint, context.getString(R.string.screen_settings_manage_subscription))
			uiState.trialState.isExpired && !uiState.hasPaidLicense -> context.getString(R.string.screen_license_check_trial_expired_info)
			else -> null
		}
		binding.tvInfoText.visibility = if (text != null) View.VISIBLE else View.GONE
		binding.tvInfoText.text = text
	}
}

private val LicenseEnforcer.LicenseUiState.hasSubscriptionUpgradeHint: Boolean
	get() = hasRunningSubscription && !hasLifetimeLicense

private val LicenseEnforcer.LicenseUiState.hasCancelSubscriptionHint: Boolean
	get() = hasRunningSubscription && hasLifetimeLicense
