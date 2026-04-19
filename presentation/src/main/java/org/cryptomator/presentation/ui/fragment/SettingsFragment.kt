package org.cryptomator.presentation.ui.fragment

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.biometric.BiometricManager
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.SwitchPreference
import org.cryptomator.presentation.BuildConfig
import org.cryptomator.presentation.CryptomatorApp
import org.cryptomator.presentation.R
import org.cryptomator.presentation.intent.Intents
import org.cryptomator.presentation.licensing.LicenseEnforcer
import org.cryptomator.presentation.presenter.ContextHolder
import org.cryptomator.presentation.service.PhotoContentJob
import org.cryptomator.presentation.service.ProductInfo
import org.cryptomator.presentation.service.resolveProductPrices
import org.cryptomator.presentation.ui.activity.AutoUploadChooseVaultActivity
import org.cryptomator.presentation.ui.activity.BiometricAuthSettingsActivity
import org.cryptomator.presentation.ui.activity.CloudSettingsActivity
import org.cryptomator.presentation.ui.activity.CryptomatorVariantsActivity
import org.cryptomator.presentation.ui.activity.LicensesActivity
import org.cryptomator.presentation.ui.activity.SettingsActivity
import org.cryptomator.presentation.ui.dialog.DebugModeDisclaimerDialog
import org.cryptomator.presentation.ui.dialog.DisableAppWhenObscuredDisclaimerDialog
import org.cryptomator.presentation.ui.dialog.DisableSecureScreenDisclaimerDialog
import org.cryptomator.presentation.ui.dialog.MicrosoftWorkaroundDisclaimerDialog
import org.cryptomator.presentation.ui.layout.PreferenceFragmentCompatLayout
import org.cryptomator.util.FlavorConfig
import org.cryptomator.util.SharedPreferencesHandler
import org.cryptomator.util.SharedPreferencesHandler.Companion.CRYPTOMATOR_VARIANTS
import org.cryptomator.util.file.LruFileCacheUtil
import java.util.function.Consumer
import java.lang.Boolean.FALSE
import java.lang.Boolean.TRUE
import java.lang.String.format
import java.lang.ref.WeakReference
import java.text.DecimalFormat
import kotlin.math.log10
import timber.log.Timber

class SettingsFragment : PreferenceFragmentCompatLayout() {

	private lateinit var sharedPreferencesHandler: SharedPreferencesHandler
	private val licenseChangeListener = Consumer<String> { _ -> setupLicense() }

	/**
	 * Loads the preferences screen and initializes preference-related handlers and UI state.
	 *
	 * This inflates preferences from R.xml.preferences, creates the fragment's SharedPreferences handler,
	 * and runs setup routines that adjust displayed values and remove or configure preferences
	 * (app version, LRU cache size, license UI, biometric availability, and Cryptomator variants).
	 */
	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		sharedPreferencesHandler = SharedPreferencesHandler(activity())
		addPreferencesFromResource(R.xml.preferences)
		isBiometricAuthenticationNotAvailableRemovePreference()
		setupAppVersion()
		setupLruCacheSize()
		setupLicense()
		setupCryptomatorVariants()
	}

	private val sendErrorReportClickListener = Preference.OnPreferenceClickListener {
		onSendErrorReportClicked()
		true
	}

	private val debugModeChangedListener = Preference.OnPreferenceChangeListener { _, newValue ->
		onDebugModeChanged(TRUE == newValue)
		true
	}

	private val clearCacheClickListener = Preference.OnPreferenceClickListener {
		LruFileCacheUtil(requireContext()).clear()
		setupLruCacheSize()
		true
	}

	private val updateCheckClickListener = Preference.OnPreferenceClickListener {
		onCheckUpdateClicked()
		true
	}

	private val clearTrustedHubHostsClickListener = Preference.OnPreferenceClickListener {
		sharedPreferencesHandler.clearTrustedHubHosts()
		Toast.makeText(requireContext(), R.string.notification_cleared_trusted_hosts, Toast.LENGTH_LONG).show()
		true
	}

	private val useAutoPhotoUploadChangedListener = Preference.OnPreferenceChangeListener { _, newValue ->
		onUseAutoPhotoUploadChanged(TRUE == newValue)
		true
	}

	private val useLruChangedListener = Preference.OnPreferenceChangeListener { _, newValue ->
		if (FALSE == newValue) {
			LruFileCacheUtil(requireContext()).clear()
			setupLruCacheSize()
		}
		Toast.makeText(context, context?.getString(R.string.screen_settings_lru_cache_changed__restart_toast), Toast.LENGTH_SHORT).show()
		true
	}

	private val disableAppWhenObscuredChangedListener = Preference.OnPreferenceChangeListener { _, newValue ->
		onDisableAppWhenObscuredChanged(TRUE == newValue)
		true
	}

	private val disableSecureScreenChangedListener = Preference.OnPreferenceChangeListener { _, newValue ->
		onDisableSecureScreenChanged(TRUE == newValue)
		true
	}

	private val screenStyleModeChangedListener = Preference.OnPreferenceChangeListener { _, newValue ->
		sharedPreferencesHandler.setScreenStyleMode(newValue as String)
		AppCompatDelegate.setDefaultNightMode(sharedPreferencesHandler.screenStyleMode)
		activity().delegate.localNightMode = sharedPreferencesHandler.screenStyleMode
		true
	}

	private val microsoftWorkaroundChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
		onMicrosoftWorkaroundChanged(TRUE == newValue)
		true
	}

	private fun activity(): SettingsActivity = this.activity as SettingsActivity

	private fun isBiometricAuthenticationNotAvailableRemovePreference() {
		val biometricAuthenticationAvailable = BiometricManager //
			.from(requireContext()) //
			.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)

		if (biometricAuthenticationAvailable != BiometricManager.BIOMETRIC_SUCCESS
			&& biometricAuthenticationAvailable != BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
		) {
			(findPreference(BIOMETRIC_AUTHENTICATION_ITEM_KEY) as Preference?)?.let { preference ->
				(findPreference(getString(R.string.screen_settings_section_general)) as PreferenceCategory?)?.removePreference(preference)
			}
			Timber //
				.tag("SettingsFragment") //
				.d("No working biometric hardware detected")
		}
	}

	private fun setupAppVersion() {
		val preference = findPreference(APP_VERSION_ITEM_KEY) as Preference?
		val versionName = SpannableString(BuildConfig.VERSION_NAME)
		versionName.setSpan( //
			ForegroundColorSpan(ContextCompat.getColor(activity(), R.color.textColorLight)), //
			0, versionName.length, 0
		)
		preference?.summaryProvider = Preference.SummaryProvider<Preference> {
			versionName
		}
	}

	/**
	 * Updates the DISPLAY_LRU_CACHE_SIZE preference summary to a colored, human-readable representation of the LRU cache size.
	 *
	 * Reads the total LRU cache size, formats it into bytes/KB/MB/GB/TB with one decimal place as needed, applies the preference text color span, and sets it as the preference's summary provider.
	 */
	private fun setupLruCacheSize() {
		val preference = findPreference(DISPLAY_LRU_CACHE_SIZE_ITEM_KEY) as Preference?

		val size = LruFileCacheUtil(requireContext()).totalSize()

		val readableSize: String = if (size > 0) {
			val units = arrayOf("B", "KB", "MB", "GB", "TB")
			val unitIndex = (log10(size.toDouble()) / 3).toInt()
			val unitValue = (1 shl unitIndex * 10).toDouble()

			(DecimalFormat("#,##0.#")
				.format(size / unitValue) + " "
					+ units[unitIndex])
		} else {
			"0 B"
		}

		val lruCacheSize = SpannableString(readableSize)

		lruCacheSize.setSpan( //
			ForegroundColorSpan(ContextCompat.getColor(activity(), R.color.textColorLight)), //
			0, lruCacheSize.length, 0
		)
		preference?.summaryProvider = Preference.SummaryProvider<Preference> {
			lruCacheSize
		}
	}

	/**
	 * Configure license-related preference UI and category entries according to build flavor and stored license/subscription state.
	 *
	 * Updates the license preference's title, summary, enabled state, and click behavior; for freemium builds it also adds or removes
	 * "manage subscription" and "upgrade to lifetime" preferences inside the license category, launches the license check intent when
	 * appropriate, and initiates the in-app purchase flow and asynchronous price resolution for the lifetime upgrade preference.
	 *
	 * Side effects:
	 * - May start activities via intents (license check, Play subscriptions URL).
	 * - May call the app's purchase flow.
	 * - Adds/removes child preferences in the license category and updates their summaries.
	 */
	private fun setupLicense() {
		val licenseCategory = findPreference(LICENSE_ITEM_KEY) as PreferenceCategory?
		val licensePref = findPreference(SharedPreferencesHandler.MAIL) as Preference?
		licensePref?.isEnabled = true
		when {
			FlavorConfig.isPremiumFlavor -> {
				licensePref?.let { pref ->
					pref.title = getString(R.string.screen_settings_license_title_unlocked)
					pref.summary = getString(R.string.screen_settings_license_summary_write_access)
					pref.onPreferenceClickListener = null
				}
				removeUpdateCheck()
			}
			FlavorConfig.isFreemiumFlavor -> {
				val licenseEnforcer = LicenseEnforcer(sharedPreferencesHandler)
				val uiState = licenseEnforcer.evaluateUiState(requireContext())
				val hasSubscription = sharedPreferencesHandler.hasRunningSubscription()
				licensePref?.let { pref ->
					if (uiState.hasPaidLicense) {
						pref.title = getString(R.string.screen_settings_license_title_unlocked)
						pref.summary = getString(R.string.screen_settings_license_summary_write_access)
						pref.onPreferenceClickListener = null
					} else {
						pref.title = getString(R.string.screen_settings_license_title_unlock)
						pref.summary = if (uiState.trialState.isActive) {
							getString(R.string.screen_settings_license_summary_trial_expires, uiState.trialState.formattedExpirationDate)
						} else {
							getString(R.string.screen_settings_license_summary_tap_to_unlock)
						}
						pref.setOnPreferenceClickListener {
							Intents.licenseCheckIntent().startActivity(activity() as ContextHolder)
							true
						}
					}
				}
				licenseCategory?.let { category ->
					val hasLifetimeLicense = sharedPreferencesHandler.licenseToken().isNotEmpty()
					if (hasSubscription) {
						if (category.findPreference<Preference>(MANAGE_SUBSCRIPTION_KEY) == null) {
							category.addPreference(Preference(requireContext()).apply {
								key = MANAGE_SUBSCRIPTION_KEY
								title = getString(R.string.screen_settings_manage_subscription)
								setOnPreferenceClickListener {
									val url = "https://play.google.com/store/account/subscriptions" +
										"?sku=${ProductInfo.PRODUCT_YEARLY_SUBSCRIPTION}" +
										"&package=${requireContext().packageName}"
									startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
									true
								}
							})
						}
					} else {
						category.findPreference<Preference>(MANAGE_SUBSCRIPTION_KEY)?.let {
							category.removePreference(it)
						}
					}
					if (hasSubscription && !hasLifetimeLicense) {
						if (category.findPreference<Preference>(UPGRADE_LIFETIME_KEY) == null) {
							category.addPreference(Preference(requireContext()).apply {
								key = UPGRADE_LIFETIME_KEY
								title = getString(R.string.screen_settings_upgrade_to_lifetime)
								setOnPreferenceClickListener {
									val app = requireActivity().application as CryptomatorApp
									app.launchPurchaseFlow(WeakReference(requireActivity()), ProductInfo.PRODUCT_FULL_VERSION)
									true
								}
							})
							val app = requireActivity().application as CryptomatorApp
							app.queryProductDetails { products ->
								val prices = products.resolveProductPrices()
								activity?.runOnUiThread {
									if (!isAdded) {
										return@runOnUiThread
									}
									category.findPreference<Preference>(UPGRADE_LIFETIME_KEY)?.let { pref ->
										if (!prices.lifetimePrice.isNullOrEmpty()) {
											pref.summary = prices.lifetimePrice
										}
									}
								}
							}
						}
					} else {
						category.findPreference<Preference>(UPGRADE_LIFETIME_KEY)?.let {
							category.removePreference(it)
						}
					}
				}
				removeUpdateCheck()
			}
			else -> {
				licensePref?.let { pref ->
					val mail = sharedPreferencesHandler.mail()
					if (mail.isEmpty()) {
						pref.title = getString(R.string.screen_settings_license_title_unlock)
						pref.summary = getString(R.string.screen_settings_license_summary_tap_to_unlock)
						pref.setOnPreferenceClickListener {
							Intents.licenseCheckIntent().startActivity(activity() as ContextHolder)
							true
						}
					} else {
						pref.title = getString(R.string.screen_settings_license_title_unlocked)
						pref.summary = format(getString(R.string.screen_settings_license_summary_write_access_mail), mail)
						pref.onPreferenceClickListener = null
					}
				}
				if (FlavorConfig.isApkStoreFlavor) {
					setupUpdateCheck()
				} else {
					removeUpdateCheck()
				}
			}
		}
	}

	private fun removeUpdateCheck() {
		val versionCategory = findPreference("versionCategory") as PreferenceCategory?
		(findPreference(UPDATE_CHECK_ITEM_KEY) as Preference?)?.let { versionCategory?.removePreference(it) }
		(findPreference(UPDATE_INTERVAL_ITEM_KEY) as Preference?)?.let { versionCategory?.removePreference(it) }
	}

	/**
	 * Updates the update-check preference's summary to show the last update check time or a "never" message.
	 *
	 * Reads the last update check timestamp from SharedPreferences, formats it using the user's date format,
	 * applies the app's light text color to the resulting string, and sets it as the summaryProvider for
	 * the preference identified by `UPDATE_CHECK_ITEM_KEY`.
	 */
	fun setupUpdateCheck() {
		val preference = findPreference(UPDATE_CHECK_ITEM_KEY) as Preference?

		val lastUpdateCheck = sharedPreferencesHandler.lastUpdateCheck()
		val readableDate: String = if (lastUpdateCheck != null) {
			val dateFormatUser = android.text.format.DateFormat.getLongDateFormat(context)
			val strDate: String = dateFormatUser.format(lastUpdateCheck)
			format(getString(R.string.screen_settings_last_check_updates), strDate)
		} else {
			getString(R.string.screen_settings_last_check_updates_never)
		}

		val date = SpannableString(readableDate)

		date.setSpan( //
			ForegroundColorSpan(ContextCompat.getColor(activity(), R.color.textColorLight)), //
			0, date.length, 0
		)
		preference?.summaryProvider = Preference.SummaryProvider<Preference> {
			date
		}
	}

	/**
	 * Removes the Cryptomator variants preference from the general settings section on premium builds.
	 *
	 * When the app is running in a premium flavor, this locates the preference identified by
	 * `CRYPTOMATOR_VARIANTS` and removes it from the "general" preference category so the option
	 * is not shown to premium users.
	 */
	private fun setupCryptomatorVariants() {
		if (FlavorConfig.isPremiumFlavor) {
			(findPreference(CRYPTOMATOR_VARIANTS) as Preference?)?.let { preference ->
				(findPreference(getString(R.string.screen_settings_section_general)) as PreferenceCategory?)?.removePreference(preference)
			}
		}
	}

	/**
	 * Registers preference click/change listeners, assigns navigation intents to preferences,
	 * and attaches the license-change listener when the fragment becomes active.
	 *
	 * This activates UI handlers for error reports, cache operations, debug and security toggles,
	 * photo upload and LRU cache controls, Microsoft workaround, and (on APK store builds) the update check.
	 * It also sets navigation intents for cloud, biometric, Cryptomator variants (omitted on premium builds),
	 * auto-upload vault chooser, and licenses, then registers `licenseChangeListener` with `sharedPreferencesHandler`.
	 */
	override fun onResume() {
		super.onResume()
		(findPreference(SEND_ERROR_REPORT_ITEM_KEY) as Preference?)?.onPreferenceClickListener = sendErrorReportClickListener
		(findPreference(LRU_CACHE_CLEAR_ITEM_KEY) as Preference?)?.onPreferenceClickListener = clearCacheClickListener
		(findPreference(CLEAR_TRUSTED_HUB_HOSTS) as Preference?)?.onPreferenceClickListener = clearTrustedHubHostsClickListener
		(findPreference(SharedPreferencesHandler.DEBUG_MODE) as Preference?)?.onPreferenceChangeListener = debugModeChangedListener
		(findPreference(SharedPreferencesHandler.DISABLE_APP_WHEN_OBSCURED) as Preference?)?.onPreferenceChangeListener = disableAppWhenObscuredChangedListener
		(findPreference(SharedPreferencesHandler.SECURE_SCREEN) as Preference?)?.onPreferenceChangeListener = disableSecureScreenChangedListener
		(findPreference(SharedPreferencesHandler.SCREEN_STYLE_MODE) as Preference?)?.onPreferenceChangeListener = screenStyleModeChangedListener
		(findPreference(SharedPreferencesHandler.PHOTO_UPLOAD) as Preference?)?.onPreferenceChangeListener = useAutoPhotoUploadChangedListener
		(findPreference(SharedPreferencesHandler.USE_LRU_CACHE) as Preference?)?.onPreferenceChangeListener = useLruChangedListener
		(findPreference(SharedPreferencesHandler.LRU_CACHE_SIZE) as Preference?)?.onPreferenceChangeListener = useLruChangedListener
		(findPreference(SharedPreferencesHandler.MICROSOFT_WORKAROUND) as Preference?)?.onPreferenceChangeListener = microsoftWorkaroundChangeListener
		if (FlavorConfig.isApkStoreFlavor) {
			(findPreference(UPDATE_CHECK_ITEM_KEY) as Preference?)?.onPreferenceClickListener = updateCheckClickListener
		}

		(findPreference(SharedPreferencesHandler.CLOUD_SETTINGS) as Preference?)?.intent = Intent(context, CloudSettingsActivity::class.java)
		(findPreference(SharedPreferencesHandler.BIOMETRIC_AUTHENTICATION) as Preference?)?.intent = Intent(context, BiometricAuthSettingsActivity::class.java)
		if (!FlavorConfig.isPremiumFlavor) {
			(findPreference(SharedPreferencesHandler.CRYPTOMATOR_VARIANTS) as Preference?)?.intent = Intent(context, CryptomatorVariantsActivity::class.java)
		}
		(findPreference(SharedPreferencesHandler.PHOTO_UPLOAD_VAULT) as Preference?)?.intent = Intent(context, AutoUploadChooseVaultActivity::class.java)
		(findPreference(SharedPreferencesHandler.LICENSES_ACTIVITY) as Preference?)?.intent = Intent(context, LicensesActivity::class.java)
		sharedPreferencesHandler.addLicenseChangedListeners(licenseChangeListener)
	}

	/**
	 * Cleans up fragment-level listeners and lifecycle state when the fragment is paused.
	 *
	 * Removes the license change listener from the shared preferences handler and then
	 * delegates to the superclass `onPause` implementation.
	 */
	override fun onPause() {
		sharedPreferencesHandler.removeLicenseChangedListeners(licenseChangeListener)
		super.onPause()
	}

	/**
	 * Disables debug mode and updates the corresponding switch in the settings UI to unchecked.
	 *
	 * This clears the debug-mode flag in shared preferences and sets the `DEBUG_MODE` SwitchPreference's
	 * checked state to `false` if the preference is present.
	 */
	fun deactivateDebugMode() {
		sharedPreferencesHandler.setDebugMode(false)
		(findPreference(SharedPreferencesHandler.DEBUG_MODE) as SwitchPreference?)?.isChecked = false
	}

	fun disableAppWhenObscured() {
		sharedPreferencesHandler.setDisableAppWhenObscured(true)
		(findPreference(SharedPreferencesHandler.DISABLE_APP_WHEN_OBSCURED) as SwitchPreference?)?.isChecked = true
	}

	fun secureScreen() {
		sharedPreferencesHandler.setSecureScreen(true)
		(findPreference(SharedPreferencesHandler.SECURE_SCREEN) as SwitchPreference?)?.isChecked = true
	}

	private fun onSendErrorReportClicked() {
		activity().presenter().onSendErrorReportClicked()
	}

	private fun onCheckUpdateClicked() {
		activity().presenter().onCheckUpdateClicked()
	}

	private fun onDebugModeChanged(enabled: Boolean) {
		if (enabled) {
			activity().showDialog(DebugModeDisclaimerDialog.newInstance())
		} else {
			activity().presenter().onDebugModeChanged(false)
		}
	}

	private fun onDisableAppWhenObscuredChanged(enabled: Boolean) {
		if (!enabled) {
			activity().showDialog(DisableAppWhenObscuredDisclaimerDialog.newInstance())
		}
	}

	private fun onDisableSecureScreenChanged(enabled: Boolean) {
		if (!enabled) {
			activity().showDialog(DisableSecureScreenDisclaimerDialog.newInstance())
		}
	}

	private fun onUseAutoPhotoUploadChanged(enabled: Boolean) {
		if (enabled) {
			activity().grantLocalStoragePermissionForAutoUpload()
		} else {
			PhotoContentJob.cancelJob(activity().applicationContext)
		}
		(findPreference(SharedPreferencesHandler.PHOTO_UPLOAD) as SwitchPreference?)?.isChecked = enabled
	}

	fun disableAutoUpload() {
		onUseAutoPhotoUploadChanged(false)
	}

	private fun onMicrosoftWorkaroundChanged(enabled: Boolean) {
		if (enabled) {
			activity().showDialog(MicrosoftWorkaroundDisclaimerDialog.newInstance())
		}
	}

	fun deactivateMicrosoftWorkaround() {
		sharedPreferencesHandler.setMicrosoftWorkaround(false)
	}

	fun rootView(): View {
		return activity().findViewById(R.id.activity_root_view)
	}

	companion object {

		private const val APP_VERSION_ITEM_KEY = "appVersion"
		private const val SEND_ERROR_REPORT_ITEM_KEY = "sendErrorReport"
		private const val BIOMETRIC_AUTHENTICATION_ITEM_KEY = "biometricAuthentication"
		private const val LICENSE_ITEM_KEY = "license"
		private const val MANAGE_SUBSCRIPTION_KEY = "manageSubscription"
		private const val UPGRADE_LIFETIME_KEY = "upgradeLifetime"
		private const val UPDATE_CHECK_ITEM_KEY = "updateCheck"
		private const val UPDATE_INTERVAL_ITEM_KEY = "updateInterval"
		private const val DISPLAY_LRU_CACHE_SIZE_ITEM_KEY = "displayLruCacheSize"
		private const val LRU_CACHE_CLEAR_ITEM_KEY = "lruCacheClear"
		private const val CLEAR_TRUSTED_HUB_HOSTS = "clearTrustedHubHosts"
	}

}
