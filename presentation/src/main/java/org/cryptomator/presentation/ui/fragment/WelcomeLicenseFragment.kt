package org.cryptomator.presentation.ui.fragment

import android.content.Context
import org.cryptomator.generator.Fragment
import org.cryptomator.presentation.CryptomatorApp
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.FragmentWelcomeLicenseBinding
import org.cryptomator.presentation.licensing.LicenseEnforcer
import org.cryptomator.presentation.ui.activity.WelcomeActivity
import org.cryptomator.presentation.ui.layout.LicenseContentViewBinder
import org.cryptomator.util.FlavorConfig

@Fragment
class WelcomeLicenseFragment : BaseFragment<FragmentWelcomeLicenseBinding>(FragmentWelcomeLicenseBinding::inflate) {

	interface Listener {
		fun onLicenseTextChanged(license: String?)
		fun onStartTrial()
		fun onSkipLicense()
		fun onLicenseViewReady()
		fun onEnterLicenseDialogRequested()
	}

	private val licenseContentViewBinder by lazy { LicenseContentViewBinder(binding.licenseContent, FlavorConfig.isFreemiumFlavor) }
	private var listener: Listener? = null

	override fun onAttach(context: Context) {
		super.onAttach(context)
		listener = context as? Listener
	}

	override fun setupView() {
		setupUi()
	}

	private fun setupUi() {
		if (FlavorConfig.isFreemiumFlavor) {
			setupIapUi()
		} else {
			setupLicenseEntryUi()
		}
	}

	private fun setupIapUi() {
		val app = requireActivity().application as CryptomatorApp
		licenseContentViewBinder.bindInitialIapLayout()
		licenseContentViewBinder.bindLegalLinks()
		licenseContentViewBinder.bindPurchaseButtons(
			activity = activity(),
			app = app,
			onTrialClicked = { listener?.onStartTrial() }
		)
		licenseContentViewBinder.loadAndBindPrices(app)
		listener?.onLicenseViewReady()
	}

	private fun setupLicenseEntryUi() {
		licenseContentViewBinder.bindInitialLicenseEntryWithTrialLayout()
		binding.licenseContent.btnTrial.text = getString(R.string.screen_welcome_trial_button)
		binding.licenseContent.btnTrial.setOnClickListener { listener?.onStartTrial() }
		licenseContentViewBinder.bindEnterLicenseButton { listener?.onEnterLicenseDialogRequested() }
		listener?.onLicenseViewReady()
	}

	fun updateState(uiState: LicenseEnforcer.LicenseUiState) {
		if (!isAdded) {
			return
		}
		licenseContentViewBinder.bindState(uiState)
	}

	fun loadAndBindPrices(app: CryptomatorApp) {
		if (!isAdded) {
			return
		}
		licenseContentViewBinder.loadAndBindPrices(app)
	}

	fun prefillLicense(license: String) {
		if (!isAdded) {
			return
		}
		listener?.onLicenseTextChanged(license)
	}

	private fun activity(): WelcomeActivity = this.activity as WelcomeActivity

}
