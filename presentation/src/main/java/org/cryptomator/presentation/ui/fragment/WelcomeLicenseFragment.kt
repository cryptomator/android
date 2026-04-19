package org.cryptomator.presentation.ui.fragment

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import org.cryptomator.generator.Fragment
import org.cryptomator.presentation.CryptomatorApp
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.FragmentWelcomeLicenseBinding
import org.cryptomator.presentation.ui.layout.LicenseContentViewBinder
import org.cryptomator.util.FlavorConfig

@Fragment
class WelcomeLicenseFragment : BaseFragment<FragmentWelcomeLicenseBinding>(FragmentWelcomeLicenseBinding::inflate) {

	interface Listener {
		/**
 * Notifies the listener that the license input has changed.
 *
 * @param license The current license text, or `null` if the input is empty or cleared.
 */
fun onLicenseTextChanged(license: String?)
		/**
 * Handle a user request to open the license (legal information) link.
 */
fun onOpenLicenseLink()
		/**
 * Signals that the user requested to start a trial period.
 */
fun onStartTrial()
		/**
 * Invoked when the user skips the license entry step.
 */
fun onSkipLicense()
	}

	private val licenseContentViewBinder by lazy { LicenseContentViewBinder(binding.licenseContent, FlavorConfig.isFreemiumFlavor) }
	private var listener: Listener? = null
	private val debounceHandler = Handler(Looper.getMainLooper())
	private var debounceRunnable: Runnable? = null

	/**
	 * Attaches the fragment to the given context and assigns the fragment's `listener` if the context
	 * implements `Listener`.
	 *
	 * @param context The context the fragment is being attached to; assigned to `listener` when it
	 * implements `WelcomeLicenseFragment.Listener`, otherwise `listener` remains `null`.
	 */
	override fun onAttach(context: Context) {
		super.onAttach(context)
		listener = context as? Listener
	}

	/**
	 * Prepares and initializes the fragment's user interface after the view is created.
	 */
	override fun setupView() {
		setupUi()
	}

	/**
	 * Removes any pending debounce callback for license input and then performs standard view teardown.
	 */
	override fun onDestroyView() {
		debounceRunnable?.let { debounceHandler.removeCallbacks(it) }
		super.onDestroyView()
	}

	/**
	 * Configure the fragment's UI for the current build flavor.
	 *
	 * Initializes the in-app purchase UI when the app is the freemium flavor; otherwise
	 * initializes the license-entry UI.
	 */
	private fun setupUi() {
		if (FlavorConfig.isFreemiumFlavor) {
			setupIapUi()
		} else {
			setupLicenseEntryUi()
		}
	}

	/**
	 * Configures the in-app purchase UI: sets the initial IAP layout, binds legal links,
	 * wires purchase and trial buttons, and loads current prices.
	 *
	 * The trial button is wired to invoke the fragment's `Listener.onStartTrial()` when clicked.
	 */
	private fun setupIapUi() {
		val app = requireActivity().application as CryptomatorApp
		licenseContentViewBinder.bindInitialIapLayout()
		licenseContentViewBinder.bindLegalLinks()
		licenseContentViewBinder.bindPurchaseButtons(
			activity = requireActivity(),
			app = app,
			onTrialClicked = { listener?.onStartTrial() }
		)
		licenseContentViewBinder.loadAndBindPrices(app)
	}

	/**
	 * Configures the license-entry UI: binds the license-with-trial layout, wires trial and license-link
	 * buttons to the fragment listener, hides the purchase button, and installs a debounced text watcher
	 * on the license input.
	 *
	 * The text watcher clears any pending callbacks on each edit and, after a delay defined by
	 * DEBOUNCE_DELAY_MS, notifies the listener of non-blank license text via `onLicenseTextChanged`.
	 * If the input is blank or null, the listener is notified immediately with `null`.
	 */
	private fun setupLicenseEntryUi() {
		licenseContentViewBinder.bindInitialLicenseEntryWithTrialLayout()
		binding.licenseContent.btnTrial.text = getString(R.string.screen_welcome_trial_button)
		binding.licenseContent.btnTrial.setOnClickListener { listener?.onStartTrial() }
		binding.licenseContent.tvLicenseLink.setOnClickListener { listener?.onOpenLicenseLink() }
		binding.licenseContent.btnPurchase.visibility = View.GONE
		binding.licenseContent.etLicense.addTextChangedListener(object : TextWatcher {
			override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
			override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
			override fun afterTextChanged(s: Editable?) {
				debounceRunnable?.let { debounceHandler.removeCallbacks(it) }
				debounceRunnable = null
				val text = s?.toString()
				if (!text.isNullOrBlank()) {
					val runnable = Runnable { listener?.onLicenseTextChanged(text) }
					debounceRunnable = runnable
					debounceHandler.postDelayed(runnable, DEBOUNCE_DELAY_MS)
				} else {
					listener?.onLicenseTextChanged(null)
				}
			}
		})
	}

	/**
	 * Update the purchase UI to reflect whether features are unlocked and whether the user has a paid license.
	 *
	 * If the fragment is not attached to its activity, the call is ignored.
	 *
	 * @param unlocked `true` if features are unlocked, `false` otherwise.
	 * @param hasPaidLicense `true` if the user holds a paid license, `false` otherwise.
	 */
	fun updateUnlocked(unlocked: Boolean, hasPaidLicense: Boolean) {
		if (!isAdded) {
			return
		}
		licenseContentViewBinder.bindPurchaseState(unlocked, hasPaidLicense)
	}

	/**
	 * Updates the UI to reflect the current trial subscription state.
	 *
	 * No-op if the fragment is not currently attached.
	 *
	 * @param active `true` if a trial is currently active, `false` otherwise.
	 * @param expired `true` if the trial has expired, `false` otherwise.
	 * @param expirationText Optional text describing the trial expiration (e.g., remaining time); `null` to clear any expiration label.
	 */
	fun updateTrialState(active: Boolean, expired: Boolean, expirationText: String?) {
		if (!isAdded) {
			return
		}
		licenseContentViewBinder.bindTrialState(active, expired, expirationText)
	}

	/**
	 * Loads product prices from the provided application and binds them to the license UI.
	 *
	 * No-op if the fragment is not attached to its activity.
	 *
	 * @param app Application instance used to load product prices.
	 */
	fun loadAndBindPrices(app: CryptomatorApp) {
		if (!isAdded) {
			return
		}
		licenseContentViewBinder.loadAndBindPrices(app)
	}

	/**
	 * Populates the license input and shows or hides the license-entry group depending on the app flavor.
	 *
	 * If the fragment is not attached, this method does nothing. When attached, it sets `etLicense` to the provided
	 * `license` string and hides `licenseEntryGroup` if `FlavorConfig.isFreemiumFlavor` is true, otherwise shows it.
	 *
	 * @param license The license string to place into the license input field.
	 */
	fun prefillLicense(license: String) {
		if (!isAdded) {
			return
		}
		binding.licenseContent.etLicense.setText(license)
		binding.licenseContent.licenseEntryGroup.visibility = if (FlavorConfig.isFreemiumFlavor) View.GONE else View.VISIBLE
	}

	companion object {
		private const val DEBOUNCE_DELAY_MS = 600L
	}
}
