package org.cryptomator.presentation.ui.layout

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import org.cryptomator.presentation.CryptomatorApp
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.ViewLicenseCheckContentBinding
import org.cryptomator.presentation.service.ProductInfo
import org.cryptomator.presentation.service.RestoreOutcomeHandler
import org.cryptomator.presentation.service.resolveProductPrices
import java.lang.ref.WeakReference

/** Shared visibility-toggling logic for the license check content included layout. */
class LicenseContentViewBinder(
	private val binding: ViewLicenseCheckContentBinding,
	private val isFreemiumFlavor: Boolean
) {

	private val context get() = binding.root.context

	/**
	 * Configure the view state for the initial in-app purchase (IAP) layout.
	 *
	 * Hides the license entry group, primary purchase button, and license link;
	 * shows the purchase options group, restore purchase link, and legal links;
	 * and disables the subscription and lifetime purchase buttons.
	 */
	fun bindInitialIapLayout() {
		binding.licenseEntryGroup.visibility = View.GONE
		binding.btnPurchase.visibility = View.GONE
		binding.tvLicenseLink.visibility = View.GONE
		binding.purchaseOptionsGroup.visibility = View.VISIBLE
		binding.tvRestorePurchase.visibility = View.VISIBLE
		binding.legalLinksGroup.visibility = View.VISIBLE
		binding.btnSubscription.isEnabled = false
		binding.btnLifetime.isEnabled = false
	}

	/**
	 * Configure the UI for non-IAP license entry mode.
	 *
	 * Shows the license entry group and the license link (setting its text to
	 * `dialog_enter_license_content`), and hides purchase options, restore purchase,
	 * and legal links.
	 */
	fun bindInitialLicenseEntryLayout() {
		binding.licenseEntryGroup.visibility = View.VISIBLE
		binding.purchaseOptionsGroup.visibility = View.GONE
		binding.tvRestorePurchase.visibility = View.GONE
		binding.legalLinksGroup.visibility = View.GONE
		binding.tvLicenseLink.visibility = View.VISIBLE
		binding.tvLicenseLink.text = context.getString(R.string.dialog_enter_license_content)
	}

	/**
	 * Configure the license-entry welcome layout with the trial row visible.
	 *
	 * Shows the purchase options and the trial row, and hides the subscription and lifetime rows
	 * along with their separating dividers.
	 */
	fun bindInitialLicenseEntryWithTrialLayout() {
		bindInitialLicenseEntryLayout()
		binding.purchaseOptionsGroup.visibility = View.VISIBLE
		binding.rowSubscription.visibility = View.GONE
		binding.rowLifetime.visibility = View.GONE
		binding.dividerTrialSubscription.visibility = View.GONE
		binding.dividerSubscriptionLifetime.visibility = View.GONE
		binding.rowTrial.visibility = View.VISIBLE
	}

	/**
	 * Opens the Cryptomator Terms and Privacy web pages when the corresponding links are tapped.
	 *
	 * Tapping the Terms link opens https://cryptomator.org/terms/ and tapping the Privacy link opens https://cryptomator.org/privacy/ in a browser.
	 */
	fun bindLegalLinks() {
		binding.tvTerms.setOnClickListener {
			context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://cryptomator.org/terms/")))
		}
		binding.tvPrivacy.setOnClickListener {
			context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://cryptomator.org/privacy/")))
		}
	}

	/**
	 * Wires purchase-related click handlers for trial, subscription, lifetime, and restore actions.
	 *
	 * @param activity Activity used as the caller context for launching purchase flows and, if resumed and implementing RestoreOutcomeHandler, receiving restore outcomes.
	 * @param app Application facade used to start purchase flows and perform restore operations; if the activity cannot receive the restore outcome, the outcome is stored on the app.
	 * @param onTrialClicked Callback invoked when the trial button is clicked.
	 */
	fun bindPurchaseButtons(
		activity: Activity,
		app: CryptomatorApp,
		onTrialClicked: () -> Unit
	) {
		binding.btnTrial.setOnClickListener { onTrialClicked() }
		binding.btnSubscription.setOnClickListener {
			app.launchPurchaseFlow(WeakReference(activity), ProductInfo.PRODUCT_YEARLY_SUBSCRIPTION)
		}
		binding.btnLifetime.setOnClickListener {
			app.launchPurchaseFlow(WeakReference(activity), ProductInfo.PRODUCT_FULL_VERSION)
		}
		binding.tvRestorePurchase.setOnClickListener {
			app.restorePurchases { outcome ->
				val handler = activity as? RestoreOutcomeHandler
				val lifecycleOwner = activity as? LifecycleOwner
				if (handler != null && lifecycleOwner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true) {
					handler.onRestoreOutcome(outcome)
				} else {
					app.lastRestoreOutcome = outcome
				}
			}
		}
	}

	/**
	 * Loads localized prices for the subscription and lifetime products and updates the corresponding buttons in the bound view.
	 *
	 * Queries product details from the provided app, resolves subscription and lifetime price strings, and posts a UI-thread update to bind those prices to the purchase buttons.
	 */
	fun loadAndBindPrices(app: CryptomatorApp) {
		app.queryProductDetails { products ->
			val prices = products.resolveProductPrices()
			binding.root.post {
				bindProductPrices(prices.subscriptionPrice, prices.lifetimePrice)
			}
		}
	}

	/**
	 * Update subscription and lifetime button labels and enable the corresponding buttons when a non-empty price is provided.
	 *
	 * If a price is `null` or empty, the corresponding button is not modified.
	 *
	 * @param subscriptionPrice The resolved subscription price string to display, or `null`/empty to leave the subscription button unchanged.
	 * @param lifetimePrice The resolved lifetime price string to display, or `null`/empty to leave the lifetime button unchanged.
	 */
	fun bindProductPrices(subscriptionPrice: String?, lifetimePrice: String?) {
		if (!subscriptionPrice.isNullOrEmpty()) {
			binding.btnSubscription.text = subscriptionPrice
			binding.btnSubscription.isEnabled = true
		}
		if (!lifetimePrice.isNullOrEmpty()) {
			binding.btnLifetime.text = lifetimePrice
			binding.btnLifetime.isEnabled = true
		}
	}

	/**
	 * Update the purchase-related UI to reflect whether the app is unlocked or a paid license is present.
	 *
	 * When the binder is configured for the freemium flavor, this toggles visibility of purchase options and the restore link based on whether a paid license exists; otherwise it enables or disables the purchase button according to the unlock state and hides trial/info views when a paid license exists.
	 *
	 * @param unlocked True if the app is currently unlocked by a license.
	 * @param hasPaidLicense True if the user has a paid (non-trial) license.
	 */
	fun bindPurchaseState(unlocked: Boolean, hasPaidLicense: Boolean) {
		if (isFreemiumFlavor) {
			binding.purchaseOptionsGroup.visibility = if (hasPaidLicense) View.GONE else View.VISIBLE
			binding.tvRestorePurchase.visibility = if (hasPaidLicense) View.GONE else View.VISIBLE
			if (hasPaidLicense) {
				binding.tvInfoText.visibility = View.GONE
				binding.tvTrialStatusBadge.visibility = View.GONE
				binding.tvTrialExpiration.visibility = View.GONE
			}
		} else {
			binding.btnPurchase.isEnabled = !unlocked
			if (hasPaidLicense) {
				binding.rowTrial.visibility = View.GONE
				binding.tvInfoText.visibility = View.GONE
			}
		}
	}

	/**
	 * Update the UI to reflect an active trial, an expired trial, or no trial.
	 *
	 * @param active True when a trial is currently active.
	 * @param expired True when a trial has expired.
	 * @param expirationText Optional text describing the trial expiration (shown when active or expired).
	 */
	fun bindTrialState(active: Boolean, expired: Boolean, expirationText: String?) {
		if (active || expired) {
			binding.trialButtonGroup.visibility = View.GONE
			binding.tvTrialStatusBadge.visibility = View.VISIBLE
			binding.tvTrialStatusBadge.text = context.getString(
				if (active) R.string.screen_license_check_trial_status_active
				else R.string.screen_license_check_trial_status_expired
			)
			binding.tvTrialExpiration.visibility = View.VISIBLE
			binding.tvTrialExpiration.text = expirationText
			if (expired) {
				binding.tvInfoText.visibility = View.VISIBLE
				binding.tvInfoText.text = context.getString(R.string.screen_license_check_trial_expired_info)
			} else {
				binding.tvInfoText.visibility = View.GONE
			}
		} else {
			binding.trialButtonGroup.visibility = View.VISIBLE
			binding.tvTrialStatusBadge.visibility = View.GONE
			binding.tvTrialExpiration.visibility = View.GONE
			binding.btnTrial.isEnabled = true
		}
	}
}
