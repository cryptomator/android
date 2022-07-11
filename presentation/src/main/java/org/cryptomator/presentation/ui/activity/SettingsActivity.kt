package org.cryptomator.presentation.ui.activity

import android.content.Intent
import android.net.Uri
import android.view.View
import org.cryptomator.generator.Activity
import org.cryptomator.presentation.R
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.presenter.SettingsPresenter
import org.cryptomator.presentation.ui.activity.view.SettingsView
import org.cryptomator.presentation.ui.dialog.AskIgnoreBatteryOptimizationsDialog
import org.cryptomator.presentation.ui.dialog.DebugModeDisclaimerDialog
import org.cryptomator.presentation.ui.dialog.DisableAppWhenObscuredDisclaimerDialog
import org.cryptomator.presentation.ui.dialog.DisableSecureScreenDisclaimerDialog
import org.cryptomator.presentation.ui.dialog.MicrosoftWorkaroundDisclaimerDialog
import org.cryptomator.presentation.ui.dialog.UpdateAppAvailableDialog
import org.cryptomator.presentation.ui.dialog.UpdateAppDialog
import org.cryptomator.presentation.ui.fragment.SettingsFragment
import javax.inject.Inject
import kotlinx.android.synthetic.main.toolbar_layout.toolbar

@Activity(layout = R.layout.activity_settings)
class SettingsActivity : BaseActivity(),
	SettingsView,
	AskIgnoreBatteryOptimizationsDialog.Callback,
	DebugModeDisclaimerDialog.Callback,
	DisableAppWhenObscuredDisclaimerDialog.Callback,
	DisableSecureScreenDisclaimerDialog.Callback,
	UpdateAppAvailableDialog.Callback,
	UpdateAppDialog.Callback,
	MicrosoftWorkaroundDisclaimerDialog.Callback {

	@Inject
	lateinit var presenter: SettingsPresenter

	override fun setupView() {
		setupToolbar()
		presenter.checkAutoUploadEnabledAndBatteryOptimizationDisabled()
	}

	private fun setupToolbar() {
		toolbar.setTitle(R.string.screen_settings_title)
		setSupportActionBar(toolbar)
	}

	fun presenter(): SettingsPresenter = presenter

	override fun onAskIgnoreBatteryOptimizationsAccepted() {
		presenter.askIgnoreBatteryOptimizationsAccepted()
	}

	override fun onAskIgnoreBatteryOptimizationsRejected(askAgain: Boolean) {
		presenter.onAskIgnoreBatteryOptimizationsRejected(askAgain);
	}

	override fun onDisclaimerAccepted() {
		presenter.onDebugModeChanged(accepted())
	}

	override fun onDisclaimerRejected() {
		settingsFragment().deactivateDebugMode()
	}

	private fun settingsFragment(): SettingsFragment = supportFragmentManager.findFragmentByTag("SettingsFragment") as SettingsFragment

	private fun accepted(): Boolean = true


	override fun onDisableAppObscuredDisclaimerAccepted() {
		// do nothing, everything set accordingly
	}

	override fun onDisableAppObscuredDisclaimerRejected() {
		settingsFragment().disableAppWhenObscured()
	}

	override fun onDisableSecureScreenDisclaimerAccepted() {
		// do nothing, everything set accordingly
	}

	override fun onDisableSecureScreenDisclaimerRejected() {
		settingsFragment().secureScreen()
	}

	fun grantLocalStoragePermissionForAutoUpload() {
		presenter.grantLocalStoragePermissionForAutoUpload()
	}

	override fun refreshUpdateTimeView() {
		settingsFragment().setupUpdateCheck()
	}

	override fun disableAutoUpload() {
		settingsFragment().disableAutoUpload()
	}

	override fun snackbarView(): View = settingsFragment().rootView()

	override fun cancelUpdateClicked() {
		// Do nothing
	}

	override fun showUpdateWebsite() {
		val url = "https://cryptomator.org/android/"
		val intent = Intent(Intent.ACTION_VIEW)
		intent.data = Uri.parse(url)
		startActivity(intent)
	}

	override fun installUpdate() {
		presenter().installUpdate()
	}

	override fun onUpdateAppDialogLoaded() {
		showProgress(ProgressModel.GENERIC)
	}

	override fun onMicrosoftDisclaimerAccepted() {
		presenter.restartApp()
	}

	override fun onMicrosoftDisclaimerRejected() {
		settingsFragment().deactivateMicrosoftWorkaround()
	}
}
