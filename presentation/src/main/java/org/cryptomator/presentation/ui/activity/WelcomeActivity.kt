package org.cryptomator.presentation.ui.activity

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import org.cryptomator.generator.Activity
import org.cryptomator.presentation.CryptomatorApp
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.ActivityWelcomeBinding
import org.cryptomator.presentation.licensing.LicenseEnforcer
import org.cryptomator.presentation.licensing.LicenseStateOrchestrator
import org.cryptomator.presentation.presenter.WelcomePresenter
import org.cryptomator.presentation.service.RestoreOutcome
import org.cryptomator.presentation.service.RestoreOutcomeHandler
import org.cryptomator.presentation.ui.activity.view.UpdateLicenseView
import org.cryptomator.presentation.ui.activity.view.WelcomeView
import org.cryptomator.presentation.ui.dialog.NoFullVersionDialog
import org.cryptomator.presentation.ui.dialog.RestoreFailedDialog
import org.cryptomator.presentation.ui.dialog.RestoreSuccessfulDialog
import org.cryptomator.presentation.ui.fragment.WelcomeIntroFragment
import org.cryptomator.presentation.ui.fragment.WelcomeLicenseFragment
import org.cryptomator.presentation.ui.fragment.WelcomeNotificationsFragment
import org.cryptomator.presentation.ui.fragment.WelcomeScreenLockFragment
import org.cryptomator.presentation.ui.layout.ObscuredAwareCoordinatorLayout
import org.cryptomator.util.FlavorConfig
import javax.inject.Inject

@Activity
class WelcomeActivity : BaseActivity<ActivityWelcomeBinding>(ActivityWelcomeBinding::inflate), //
	UpdateLicenseView, //
	WelcomeView, //
	WelcomeLicenseFragment.Listener, //
	WelcomeNotificationsFragment.Listener, //
	WelcomeScreenLockFragment.Listener, //
	RestoreOutcomeHandler, //
	RestoreSuccessfulDialog.Callback, //
	NoFullVersionDialog.Callback, //
	RestoreFailedDialog.Callback {

	@Inject
	lateinit var welcomePresenter: WelcomePresenter

	@Inject
	lateinit var licenseEnforcer: LicenseEnforcer

	private val keyguardManager by lazy { getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager }

	private val orchestrator by lazy {
		LicenseStateOrchestrator(
			sharedPreferencesHandler, licenseEnforcer, { this },
			target = object : LicenseStateOrchestrator.Target {
				/**
				 * Updates the license page UI to reflect the current purchase and trial status.
				 *
				 * Updates the license fragment's unlocked state based on write-access and paid-license flags.
				 * For non-premium, non-freemium flavors without a paid license, also evaluates and updates the trial state shown on the license fragment.
				 *
				 * @param hasWriteAccess True if the current user/license grants write access (unlocked features).
				 * @param hasPaidLicense True if a paid license is present.
				 */
				override fun onPurchaseStateChanged(hasWriteAccess: Boolean, hasPaidLicense: Boolean) {
					if (!this@WelcomeActivity::pagerAdapter.isInitialized) {
						return
					}
					pagerAdapter.licenseFragment?.updateUnlocked(hasWriteAccess, hasPaidLicense)
					if (!FlavorConfig.isPremiumFlavor && !FlavorConfig.isFreemiumFlavor && !hasPaidLicense) {
						val uiState = licenseEnforcer.evaluateUiState(this@WelcomeActivity)
						pagerAdapter.licenseFragment?.updateTrialState(
							uiState.trialState.isActive,
							uiState.trialState.isExpired,
							uiState.trialExpirationText
						)
					}
				}
				/**
				 * Notify the activity that the trial state changed and propagate the state to the license fragment.
				 *
				 * @param active `true` if a trial is currently active, `false` otherwise.
				 * @param expired `true` if the trial has expired, `false` otherwise.
				 * @param expirationText Human-readable remaining time or expiration information, or `null` if not available.
				 */
				override fun onTrialStateChanged(active: Boolean, expired: Boolean, expirationText: String?) {
					if (!this@WelcomeActivity::pagerAdapter.isInitialized) {
						return
					}
					pagerAdapter.licenseFragment?.updateTrialState(active, expired, expirationText)
				}
			},
			priceLoader = {
				if (this@WelcomeActivity::pagerAdapter.isInitialized) {
					pagerAdapter.licenseFragment?.loadAndBindPrices(application as CryptomatorApp)
				}
			}
		)
	}

	private lateinit var pagerAdapter: WelcomePagerAdapter
	private val pages = mutableListOf<FragmentPage>()
	private var navBasePaddingBottom: Int = 0

	/**
	 * Validates a newly delivered intent for onboarding-related data so the activity can react (e.g., handle deep links).
	 */
	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		validate(intent)
	}

	/**
	 * Initializes the welcome screen UI and onboarding pager, or navigates away if the welcome flow is already completed.
	 *
	 * Configures the toolbar, installs a security-aware touch listener, applies bottom insets padding to the navigation container,
	 * constructs pager pages and adapter, validates the incoming intent, and refreshes notification permission, license orchestrator,
	 * and screen-lock state. If the persistent "welcome completed" flag is set, opens the vault list and finishes setup immediately.
	 */
	override fun setupView() {
		if (sharedPreferencesHandler.hasCompletedWelcomeFlow()) {
			openVaultList()
			return
		}

		setSupportActionBar(binding.mtToolbar.toolbar)
		supportActionBar?.title = getString(R.string.screen_welcome_title)
		supportActionBar?.setDisplayHomeAsUpEnabled(false)
		binding.mtToolbar.toolbar.navigationIcon = null

		binding.activityRootView.setOnFilteredTouchEventForSecurityListener(object : ObscuredAwareCoordinatorLayout.Listener {
			override fun onFilteredTouchEventForSecurity() {
				welcomePresenter.onFilteredTouchEventForSecurity()
			}
		})
		navBasePaddingBottom = binding.navigationContainer.paddingBottom
		ViewCompat.setOnApplyWindowInsetsListener(binding.navigationContainer) { view, insets ->
			val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
			val extra = (8 * resources.displayMetrics.density).toInt()
			view.updatePadding(bottom = navBasePaddingBottom + systemBars.bottom + extra)
			insets
		}

		setupPages()
		setupPager()

		validate(intent)
		updateNotificationPermissionState()
		orchestrator.updateState()
		updateScreenLockState()
	}

	/**
	 * Resumes onboarding-related state and handles any pending restore outcome.
	 *
	 * If the welcome flow has already been completed and the activity is not finishing, opens the vault list and finishes the activity. Otherwise, resumes the license state orchestrator, consumes and dispatches a pending restore outcome (if any), and refreshes notification-permission and screen-lock UI state.
	 */
	override fun onResume() {
		super.onResume()
		if (sharedPreferencesHandler.hasCompletedWelcomeFlow() && !isFinishing) {
			openVaultList()
			return
		}
		orchestrator.onResume()
		(application as CryptomatorApp).consumeLastRestoreOutcome()?.let { onRestoreOutcome(it) }
		updateNotificationPermissionState()
		updateScreenLockState()
	}

	/**
	 * Propagates the activity pause lifecycle event to the LicenseStateOrchestrator.
	 */
	override fun onPause() {
		super.onPause()
		orchestrator.onPause()
	}

	/**
	 * Populates the onboarding pager's page list in the correct order for the welcome flow.
	 *
	 * The sequence is Intro, an optional License page (included only when the build is not the premium
	 * flavor), Notifications, and ScreenLock.
	 */
	private fun setupPages() {
		pages.clear()
		pages.add(FragmentPage.Intro)
		if (!FlavorConfig.isPremiumFlavor) {
			pages.add(FragmentPage.License)
		}
		pages.add(FragmentPage.Notifications)
		pages.add(FragmentPage.ScreenLock)
	}

	/**
	 * Initializes the welcome ViewPager2: attaches the pager adapter, sets the initial page and user input,
	 * registers page-change handling to update navigation UI and per-page state, and wires Back/Next button actions.
	 *
	 * The page-change handler updates navigation buttons and refreshes page-specific state:
	 * - License page: refreshes license orchestrator state.
	 * - Notifications page: refreshes notification-permission UI state.
	 * - ScreenLock page: refreshes keyguard/lock UI state.
	 *
	 * The Back button moves to the previous page when possible. The Next button advances to the next page or completes onboarding when on the last page.
	 */
	private fun setupPager() {
		pagerAdapter = WelcomePagerAdapter(this, pages)
		binding.welcomePager.adapter = pagerAdapter
		binding.welcomePager.setCurrentItem(0, false)
		binding.welcomePager.isUserInputEnabled = true
		binding.welcomePager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
			override fun onPageSelected(position: Int) {
				updateNavigationButtons(position)
				when (pages[position]) {
					is FragmentPage.License -> orchestrator.updateState()
					is FragmentPage.Notifications -> updateNotificationPermissionState()
					is FragmentPage.ScreenLock -> updateScreenLockState()
					is FragmentPage.Intro -> Unit
				}
			}
		})
		updateNavigationButtons(0)
		binding.btnBack.setOnClickListener {
			val pos = binding.welcomePager.currentItem
			if (pos > 0) {
				binding.welcomePager.currentItem = pos - 1
			}
		}
		binding.btnNext.setOnClickListener {
			advanceOrComplete()
		}
	}

	/**
	 * Update navigation button visibility and text according to the given page index.
	 *
	 * @param position The index of the currently selected page; 0 is the first page. */
	private fun updateNavigationButtons(position: Int) {
		binding.btnBack.visibility = if (position == 0) View.INVISIBLE else View.VISIBLE
		binding.btnNext.text = if (position == pagerAdapter.itemCount - 1) {
			getString(R.string.screen_welcome_continue_button)
		} else {
			getString(R.string.next)
		}
	}

	/**
	 * Determines whether the runtime requires the runtime notification permission.
	 *
	 * @return `true` if the device is running a platform newer than Android S_V2 (i.e., POST_NOTIFICATIONS must be requested), `false` otherwise.
	 */
	private fun needsNotificationPermission(): Boolean {
		return Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2
	}

	/**
	 * Determines whether notification posting is permitted for this app on the device.
	 *
	 * @return `true` if `POST_NOTIFICATIONS` is granted or the API level does not require the permission, `false` otherwise.
	 */
	private fun hasNotificationPermission(): Boolean {
		return !needsNotificationPermission() || ContextCompat.checkSelfPermission(
			this,
			Manifest.permission.POST_NOTIFICATIONS
		) == PackageManager.PERMISSION_GRANTED
	}

	/**
	 * Updates the notifications page with the current notification permission state.
	 *
	 * If `grantedOverride` is provided, that value is used; otherwise the method queries
	 * the system permission state and forwards the result to the notifications fragment.
	 *
	 * @param grantedOverride Optional override for the permission state; when non-null, this value is applied instead of reading the system permission.
	 */
	private fun updateNotificationPermissionState(grantedOverride: Boolean? = null) {
		if (!this::pagerAdapter.isInitialized) {
			return
		}
		val granted = grantedOverride ?: hasNotificationPermission()
		pagerAdapter.notificationsFragment?.updatePermissionState(granted)
	}

	/**
	 * Update the ScreenLock page's UI to reflect whether the device's screen lock is secure.
	 *
	 * If the pager adapter is initialized and the ScreenLock fragment is present, the fragment's
	 * screen-lock state is updated from `keyguardManager.isKeyguardSecure`. Otherwise this is a no-op.
	 */
	private fun updateScreenLockState() {
		if (!this::pagerAdapter.isInitialized) {
			return
		}
		pagerAdapter.screenLockFragment?.updateScreenLockState(keyguardManager.isKeyguardSecure)
	}

	/**
	 * Completes onboarding by recording completion and navigating to the vault list.
	 *
	 * Marks the welcome flow as completed, records that the screen-lock dialog has already been shown,
	 * then opens the vault list and finishes the activity.
	 */
	private fun completeWelcomeFlow() {
		sharedPreferencesHandler.setWelcomeFlowCompleted()
		sharedPreferencesHandler.setScreenLockDialogAlreadyShown()
		openVaultList()
	}

	/**
	 * Finalizes onboarding by setting the activity result to `RESULT_OK` and closing the activity.
	 */
	private fun openVaultList() {
		setResult(RESULT_OK)
		finish()
	}

	/**
	 * Validates the intent's data URI with the welcome presenter when applicable.
	 *
	 * If the provided intent contains a data URI and the app is not a premium flavor,
	 * the URI is forwarded to the welcome presenter for validation. No action is taken
	 * for a null intent, an intent without data, or when running the premium flavor.
	 *
	 * @param intent The incoming intent that may contain a data URI to validate.
	 */
	private fun validate(intent: Intent?) {
		val data = intent?.data
		if (data != null && !FlavorConfig.isPremiumFlavor) {
			welcomePresenter.validate(data)
		}
	}

	/**
	 * Prefills the license entry field in the license onboarding page with the provided license string.
	 *
	 * @param license The license text or key to populate into the license input field.
	 */
	override fun showOrUpdateLicenseEntry(license: String) {
		pagerAdapter.licenseFragment?.prefillLicense(license)
	}

	/**
	 * Handles a confirmed license email during onboarding by refreshing license state and advancing to the next onboarding page without presenting a confirmation dialog.
	 *
	 * @param mail The email address associated with the confirmed license.
	 */
	override fun showConfirmationDialog(mail: String) {
		orchestrator.updateState()
		autoAdvanceToNextPage()
	}

	/**
	 * Updates the onboarding UI's notification-permission state based on the result of a permission request.
	 *
	 * @param granted `true` if notification permission was granted, `false` otherwise.
	 */
	override fun onNotificationPermissionResult(granted: Boolean) {
		updateNotificationPermissionState(granted)
	}

	/**
	 * Validate the provided license text and update the license dialog's validation state.
	 *
	 * @param license The license text entered by the user, or `null` if the input was cleared.
	 */

	override fun onLicenseTextChanged(license: String?) {
		welcomePresenter.validateDialogAware(license)
	}

	/**
	 * Opens the Cryptomator Android license page in a web browser.
	 *
	 * Launches an ACTION_VIEW intent for https://cryptomator.org/android/.
	 */
	override fun onOpenLicenseLink() {
		startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://cryptomator.org/android/")))
	}

	/**
	 * Starts a trial license, refreshes the license state, and advances the onboarding pager to the next page.
	 */
	override fun onStartTrial() {
		licenseEnforcer.startTrial()
		orchestrator.updateState()
		autoAdvanceToNextPage()
	}

	/**
	 * Skips the license step and continues the onboarding flow.
	 *
	 * Advances to the next onboarding page or completes the welcome flow if currently on the last page.
	 */
	override fun onSkipLicense() {
		advanceOrComplete()
	}

	/**
	 * Shows a dialog corresponding to the restore operation outcome.
	 *
	 * For RestoreOutcome.RESTORED shows a successful-restore dialog, for NOTHING_TO_RESTORE shows a no-full-version dialog,
	 * and for RestoreOutcome.FAILED shows a failed-restore dialog.
	 *
	 * @param outcome The result of a restore attempt.
	 */

	override fun onRestoreOutcome(outcome: RestoreOutcome) {
		when (outcome) {
			RestoreOutcome.RESTORED -> showDialog(RestoreSuccessfulDialog.newInstance())
			RestoreOutcome.NOTHING_TO_RESTORE -> showDialog(NoFullVersionDialog.newInstance())
			is RestoreOutcome.FAILED -> showDialog(RestoreFailedDialog.newInstance())
		}
	}

	/**
 * Called when the restore-successful dialog is dismissed.
 *
 * Default no-op implementation.
 */
override fun onRestoreSuccessfulDialogFinished() = Unit
	/**
 * Called when the "no full version" restore dialog is dismissed.
 *
 * This implementation intentionally performs no action.
 */
override fun onNoFullVersionDialogFinished() = Unit
	/**
 * Called when the restore-failed dialog is dismissed.
 *
 * No action is performed in this implementation.
 */
override fun onRestoreFailedDialogFinished() = Unit

	/**
	 * Initiates the notification permission request flow.
	 *
	 * Starts the process to obtain the runtime POST_NOTIFICATIONS permission from the user.
	 */

	override fun onRequestNotifications() {
		welcomePresenter.requestNotificationPermission()
	}

	/**
	 * Requests enabling or disabling the device screen lock via the presenter.
	 *
	 * @param setScreenLock `true` to enable the device screen lock, `false` to disable it.
	 */

	override fun onSetScreenLock(setScreenLock: Boolean) {
		welcomePresenter.onSetScreenLock(setScreenLock)
	}

	/**
	 * Advances the onboarding pager to the next page or finishes the welcome flow when on the last page.
	 */
	private fun advanceOrComplete() {
		val pos = binding.welcomePager.currentItem
		if (pos < pagerAdapter.itemCount - 1) {
			binding.welcomePager.currentItem = pos + 1
		} else {
			completeWelcomeFlow()
		}
	}

	/**
	 * Schedules an automatic advance to the next pager page after a short delay.
	 *
	 * If the activity is still active, the pager remains on the same source page, and the source page
	 * is not the last page, the pager will move forward by one page after `AUTO_ADVANCE_DELAY_MS`.
	 */
	private fun autoAdvanceToNextPage() {
		val sourcePage = binding.welcomePager.currentItem
		binding.welcomePager.postDelayed({
			if (!isFinishing && binding.welcomePager.currentItem == sourcePage && sourcePage < pagerAdapter.itemCount - 1) {
				binding.welcomePager.currentItem = sourcePage + 1
			}
		}, AUTO_ADVANCE_DELAY_MS)
	}

	private sealed class FragmentPage {
		object Intro : FragmentPage()
		object License : FragmentPage()
		object Notifications : FragmentPage()
		object ScreenLock : FragmentPage()
	}

	private inner class WelcomePagerAdapter(activity: AppCompatActivity, private val pages: List<FragmentPage>) : androidx.viewpager2.adapter.FragmentStateAdapter(activity) {

		val licenseFragment: WelcomeLicenseFragment?
			get() = findPageFragment<FragmentPage.License, WelcomeLicenseFragment>()

		val notificationsFragment: WelcomeNotificationsFragment?
			get() = findPageFragment<FragmentPage.Notifications, WelcomeNotificationsFragment>()

		val screenLockFragment: WelcomeScreenLockFragment?
			get() = findPageFragment<FragmentPage.ScreenLock, WelcomeScreenLockFragment>()

		/**
		 * Returns the fragment instance for the first page of type `P` if that page's fragment is currently instantiated.
		 *
		 * @return The fragment of type `F` for the first matching page, or `null` if no matching page exists or its fragment is not created.
		 */
		private inline fun <reified P : FragmentPage, reified F : Fragment> findPageFragment(): F? {
			val pos = pages.indexOfFirst { it is P }
			return if (pos >= 0) supportFragmentManager.findFragmentByTag("f$pos") as? F else null
		}

		/**
 * Provides the number of pages the adapter exposes.
 *
 * @return The number of pages in the adapter.
 */
override fun getItemCount(): Int = pages.size

		/**
		 * Creates the Fragment instance for the page at the given position.
		 *
		 * @param position The index of the page in the adapter's pages list.
		 * @return The Fragment associated with that page (Intro, License, Notifications, or ScreenLock).
		 */
		override fun createFragment(position: Int): Fragment {
			return when (pages[position]) {
				is FragmentPage.Intro -> WelcomeIntroFragment()
				is FragmentPage.License -> WelcomeLicenseFragment()
				is FragmentPage.Notifications -> WelcomeNotificationsFragment()
				is FragmentPage.ScreenLock -> WelcomeScreenLockFragment()
			}
		}
	}

	companion object {
		private const val AUTO_ADVANCE_DELAY_MS = 500L
	}
}
