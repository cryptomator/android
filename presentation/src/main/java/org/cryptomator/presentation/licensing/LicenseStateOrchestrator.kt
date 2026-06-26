package org.cryptomator.presentation.licensing

import org.cryptomator.util.FlavorConfig
import org.cryptomator.util.SharedPreferencesHandler
import java.util.function.Consumer

class LicenseStateOrchestrator(
	private val sharedPreferencesHandler: SharedPreferencesHandler,
	private val licenseEnforcer: LicenseEnforcer,
	private val callback: Callback,
	private val priceLoader: (() -> Unit)? = null
) {

	interface Callback {
		fun onLicenseStateChanged(uiState: LicenseEnforcer.LicenseUiState)
		fun onSubscriptionActivatedFirstTime() {}
	}

	private val licenseChangeListener = Consumer<String> { _ -> updateState() }
	private var wasSubscriptionOnly = false

	fun onResume() {
		wasSubscriptionOnly = isSubscriptionOnly(
			hasRunningSubscription = sharedPreferencesHandler.hasRunningSubscription(),
			hasLifetimeLicense = sharedPreferencesHandler.licenseToken().isNotEmpty()
		)
		sharedPreferencesHandler.addLicenseChangedListeners(licenseChangeListener)
		if (FlavorConfig.isFreemiumFlavor) {
			priceLoader?.invoke()
		}
	}

	fun onPause() {
		sharedPreferencesHandler.removeLicenseChangedListeners(licenseChangeListener)
	}

	fun updateState() {
		val uiState = licenseEnforcer.evaluateUiState()
		val nowSubOnly = isSubscriptionOnly(uiState.hasRunningSubscription, uiState.hasLifetimeLicense)
		val wasSubOnly = wasSubscriptionOnly
		wasSubscriptionOnly = nowSubOnly

		if (!wasSubOnly && nowSubOnly) {
			callback.onSubscriptionActivatedFirstTime()
		}
		callback.onLicenseStateChanged(uiState)
	}

	private fun isSubscriptionOnly(hasRunningSubscription: Boolean, hasLifetimeLicense: Boolean): Boolean =
		hasRunningSubscription && !hasLifetimeLicense
}
