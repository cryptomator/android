package org.cryptomator.presentation.ui.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import org.cryptomator.generator.Activity
import org.cryptomator.generator.InjectIntent
import org.cryptomator.presentation.CryptomatorApp
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.ActivityLicenseCheckBinding
import org.cryptomator.presentation.intent.Intents.vaultListIntent
import org.cryptomator.presentation.intent.LicenseCheckIntent
import org.cryptomator.presentation.licensing.LicenseEnforcer
import org.cryptomator.presentation.licensing.LicenseStateOrchestrator
import org.cryptomator.presentation.presenter.LicenseCheckPresenter
import org.cryptomator.presentation.service.RestoreOutcome
import org.cryptomator.presentation.service.RestoreOutcomeHandler
import org.cryptomator.presentation.ui.activity.view.UpdateLicenseView
import org.cryptomator.presentation.ui.dialog.LicenseConfirmationDialog
import org.cryptomator.presentation.ui.dialog.NoFullVersionDialog
import org.cryptomator.presentation.ui.dialog.RestoreFailedDialog
import org.cryptomator.presentation.ui.dialog.RestoreSuccessfulDialog
import org.cryptomator.presentation.ui.layout.LicenseContentViewBinder
import org.cryptomator.presentation.ui.layout.ObscuredAwareCoordinatorLayout
import org.cryptomator.util.FlavorConfig
import javax.inject.Inject

@Activity
class LicenseCheckActivity : BaseActivity<ActivityLicenseCheckBinding>(ActivityLicenseCheckBinding::inflate), //
	LicenseConfirmationDialog.Callback, //
	UpdateLicenseView, //
	RestoreOutcomeHandler, //
	RestoreSuccessfulDialog.Callback, //
	NoFullVersionDialog.Callback, //
	RestoreFailedDialog.Callback {

	@Inject
	lateinit var licenseCheckPresenter: LicenseCheckPresenter

	@Inject
	lateinit var licenseEnforcer: LicenseEnforcer

	@InjectIntent
	lateinit var licenseCheckIntent: LicenseCheckIntent

	private var lockedAction: LicenseEnforcer.LockedAction? = null
	private val licenseContentViewBinder by lazy { LicenseContentViewBinder(binding.licenseContent, FlavorConfig.isFreemiumFlavor) }

	private val orchestrator by lazy {
		LicenseStateOrchestrator(
			sharedPreferencesHandler, licenseEnforcer, { this },
			target = object : LicenseStateOrchestrator.Target {
				/**
				 * Handles changes to purchase state by updating the license content view.
				 *
				 * @param hasWriteAccess `true` if the current purchase state grants write access to app features, `false` otherwise.
				 * @param hasPaidLicense `true` if a paid license is present, `false` otherwise.
				 */
				override fun onPurchaseStateChanged(hasWriteAccess: Boolean, hasPaidLicense: Boolean) {
					licenseContentViewBinder.bindPurchaseState(hasWriteAccess, hasPaidLicense)
				}
				/**
				 * Updates the UI to reflect the current trial status.
				 *
				 * @param active `true` if a trial is currently active, `false` otherwise.
				 * @param expired `true` if the trial has expired, `false` otherwise.
				 * @param expirationText Human-readable text describing the trial expiration (e.g., date), or `null` if not available.
				 */
				override fun onTrialStateChanged(active: Boolean, expired: Boolean, expirationText: String?) {
					licenseContentViewBinder.bindTrialState(active, expired, expirationText)
				}
			},
			priceLoader = { licenseContentViewBinder.loadAndBindPrices(application as CryptomatorApp) }
		)
	}

	/**
	 * Initializes the activity, registers a security touch listener, and validates the incoming intent.
	 *
	 * Registers a listener on the root view that forwards filtered touch events to the presenter for security
	 * and invokes intent validation using the current activity intent.
	 */
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		binding.activityRootView.setOnFilteredTouchEventForSecurityListener(object : ObscuredAwareCoordinatorLayout.Listener {
			override fun onFilteredTouchEventForSecurity() {
				licenseCheckPresenter.onFilteredTouchEventForSecurity()
			}
		})
		validate(intent)
	}

	/**
	 * Resumes license state orchestration and processes any pending restore outcome.
	 *
	 * Invokes the orchestrator's resume logic and, if the application has a pending restore outcome, consumes it and forwards it to onRestoreOutcome.
	 */
	override fun onResume() {
		super.onResume()
		orchestrator.onResume()
		(application as CryptomatorApp).consumeLastRestoreOutcome()?.let { onRestoreOutcome(it) }
	}

	/**
	 * Pauses the license state orchestrator when the activity is paused.
	 */
	override fun onPause() {
		super.onPause()
		orchestrator.onPause()
	}

	/**
	 * Initializes the activity's view state by deriving the current locked action from the injected intent
	 * and configuring the upsell UI accordingly.
	 */
	override fun setupView() {
		lockedAction = LicenseEnforcer.LockedAction.fromName(licenseCheckIntent.lockedAction())
		setupUpsellView()
	}

	/**
	 * Configures the upsell UI: sets up the toolbar, displays or hides the info text for any locked action,
	 * and selects the appropriate mode-specific content.
	 *
	 * If a `lockedAction` is present, its header message is shown in the info text; otherwise the info text is hidden.
	 * Chooses the in-app-purchase UI for freemium builds and the license-entry UI for non-freemium builds.
	 */
	private fun setupUpsellView() {
		setSupportActionBar(binding.mtToolbar.toolbar)
		supportActionBar?.setDisplayHomeAsUpEnabled(true)
		supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_clear)
		binding.mtToolbar.toolbar.setNavigationOnClickListener { finish() }

		val action = lockedAction
		if (action != null) {
			binding.licenseContent.tvInfoText.visibility = View.VISIBLE
			binding.licenseContent.tvInfoText.text = getString(action.headerMessageRes)
		} else {
			binding.licenseContent.tvInfoText.visibility = View.GONE
			binding.licenseContent.tvInfoText.text = null
		}

		if (FlavorConfig.isFreemiumFlavor) {
			setupIapView()
		} else {
			setupLicenseEntryView()
		}
	}

	/**
	 * Configures the activity UI for the in-app purchase (full-version) flow.
	 *
	 * Sets the action bar title, binds the IAP-specific content and legal links, and configures
	 * the purchase buttons. Activating the trial option starts a trial via the license enforcer
	 * and refreshes the license state.
	 */
	private fun setupIapView() {
		supportActionBar?.title = getString(R.string.screen_license_check_title_full_version)
		licenseContentViewBinder.bindInitialIapLayout()
		licenseContentViewBinder.bindLegalLinks()
		licenseContentViewBinder.bindPurchaseButtons(
			activity = this,
			app = application as CryptomatorApp,
			onTrialClicked = {
				licenseEnforcer.startTrial()
				orchestrator.updateState()
			}
		)
	}

	/**
	 * Configures the UI for entering a license key.
	 *
	 * Sets the action bar title, binds the license-entry layout, shows the purchase/submit button
	 * with the "OK" label and hooks it to submit the entered license, and makes the license link
	 * open the Cryptomator Android website when tapped.
	 */
	private fun setupLicenseEntryView() {
		supportActionBar?.title = getString(R.string.screen_license_check_title)
		licenseContentViewBinder.bindInitialLicenseEntryLayout()
		binding.licenseContent.btnPurchase.visibility = View.VISIBLE
		binding.licenseContent.btnPurchase.text = getString(R.string.dialog_enter_license_ok_button)
		binding.licenseContent.btnPurchase.setOnClickListener { onLicenseSubmit() }
		binding.licenseContent.tvLicenseLink.setOnClickListener {
			startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://cryptomator.org/android/")))
		}
	}

	/**
	 * Handle a newly delivered intent by updating activity state, recomputing the locked action,
	 * refreshing the upsell UI, updating the license orchestrator, and validating the intent data.
	 *
	 * @param intent The new intent delivered to the activity.
	 */
	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		setIntent(intent)
		Activities.setIntent(this)
		lockedAction = LicenseEnforcer.LockedAction.fromName(licenseCheckIntent.lockedAction())
		setupUpsellView()
		orchestrator.updateState()
		validate(intent)
	}

	/**
	 * Validates the intent's data URI using the license check presenter.
	 *
	 * @param intent The incoming Intent whose `data` Uri will be validated.
	 */
	private fun validate(intent: Intent) {
		val data: Uri? = intent.data
		licenseCheckPresenter.validate(data)
	}

	/**
	 * Populates the license input with the provided text and shows the license entry UI.
	 *
	 * @param license The license string to display in the license input field.
	 */
	override fun showOrUpdateLicenseEntry(license: String) {
		binding.licenseContent.etLicense.setText(license)
		binding.licenseContent.licenseEntryGroup.visibility = View.VISIBLE
	}

	/**
	 * Shows a confirmation dialog to confirm the license associated with the provided email address.
	 *
	 * @param mail The email address to display in the confirmation dialog.
	 */
	override fun showConfirmationDialog(mail: String) {
		showDialog(LicenseConfirmationDialog.newInstance(mail))
	}

	/**
	 * Navigates to the vault list screen and prevents returning to this activity via the back stack.
	 */
	override fun licenseConfirmationClicked() {
		vaultListIntent() //
			.preventGoingBackInHistory() //
			.startActivity(this) //
	}

	/**
	 * Shows the dialog corresponding to a restore outcome.
	 *
	 * Displays a success dialog when the outcome is `RESTORED`, a no-full-version dialog when `NOTHING_TO_RESTORE`,
	 * and a failure dialog for any `FAILED` outcome.
	 *
	 * @param outcome The result of a restore operation that determines which dialog to display.
	 */
	override fun onRestoreOutcome(outcome: RestoreOutcome) {
		when (outcome) {
			RestoreOutcome.RESTORED -> showDialog(RestoreSuccessfulDialog.newInstance())
			RestoreOutcome.NOTHING_TO_RESTORE -> showDialog(NoFullVersionDialog.newInstance())
			is RestoreOutcome.FAILED -> showDialog(RestoreFailedDialog.newInstance())
		}
	}

	/**
 * Called when the restore-successful dialog is finished by the user.
 *
 * Default implementation performs no action.
 */
override fun onRestoreSuccessfulDialogFinished() = Unit
	/**
 * Callback invoked when the "no full version" dialog is dismissed.
 *
 * No action is performed by this implementation.
 */
override fun onNoFullVersionDialogFinished() = Unit
	/**
 * Called when the restore-failed dialog is finished; no action is taken.
 */
override fun onRestoreFailedDialogFinished() = Unit

	/**
	 * Submits the current license text from the input field to the presenter for validation and dialog-aware handling.
	 */
	private fun onLicenseSubmit() {
		licenseCheckPresenter.validateDialogAware(binding.licenseContent.etLicense.text?.toString())
	}

}
