package org.cryptomator.presentation.licensing

import android.content.Context
import org.cryptomator.util.FlavorConfig
import org.cryptomator.util.SharedPreferencesHandler
import java.util.function.Consumer

class LicenseStateOrchestrator(
	private val sharedPreferencesHandler: SharedPreferencesHandler,
	private val licenseEnforcer: LicenseEnforcer,
	private val contextProvider: () -> Context,
	private val target: Target,
	private val priceLoader: (() -> Unit)? = null
) {

	interface Target {
		/**
 * Notifies the target about an update to the user's purchase and license status.
 *
 * @param hasWriteAccess Whether the user currently has write access to premium functionality.
 * @param hasPaidLicense Whether the user currently holds a paid license.
 */
fun onPurchaseStateChanged(hasWriteAccess: Boolean, hasPaidLicense: Boolean)
		/**
 * Updates the UI about the current trial status.
 *
 * @param active True if a trial is currently active.
 * @param expired True if the trial has expired.
 * @param expirationText Localized text describing the trial expiration (e.g., date or remaining time), or `null` if unavailable.
 */
fun onTrialStateChanged(active: Boolean, expired: Boolean, expirationText: String?)
	}

	private val licenseChangeListener = Consumer<String> { _ -> updateState() }

	/**
	 * Registers the license-change listener, synchronizes the UI with the current license state, and triggers optional price loading for freemium builds.
	 *
	 * Calls into the shared preferences handler to listen for license changes, immediately updates the target UI state, and invokes `priceLoader` if the build is a freemium flavor and a loader was provided.
	 */
	fun onResume() {
		sharedPreferencesHandler.addLicenseChangedListeners(licenseChangeListener)
		updateState()
		if (FlavorConfig.isFreemiumFlavor) {
			priceLoader?.invoke()
		}
	}

	/**
	 * Unregisters the orchestrator's license-change listener from the SharedPreferencesHandler.
	 *
	 * Call when the hosting component is paused to stop receiving license change events.
	 */
	fun onPause() {
		sharedPreferencesHandler.removeLicenseChangedListeners(licenseChangeListener)
	}

	/**
	 * Synchronizes the current license UI state and notifies the target about purchase and trial status.
	 *
	 * Evaluates the current license UI state and calls the target's purchase-state callback.
	 * If the build is freemium and no paid license is present, also notifies the target of the trial state
	 * (active/expired and optional expiration text).
	 */
	fun updateState() {
		val uiState = licenseEnforcer.evaluateUiState(contextProvider())
		target.onPurchaseStateChanged(uiState.hasWriteAccess, uiState.hasPaidLicense)
		if (FlavorConfig.isFreemiumFlavor && !uiState.hasPaidLicense) {
			target.onTrialStateChanged(
				uiState.trialState.isActive, uiState.trialState.isExpired, uiState.trialExpirationText
			)
		}
	}
}
