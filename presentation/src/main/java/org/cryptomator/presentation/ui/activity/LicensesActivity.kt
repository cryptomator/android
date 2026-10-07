package org.cryptomator.presentation.ui.activity

import org.cryptomator.generator.Activity
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.ActivityLicensesBinding

@Activity
class LicensesActivity : BaseActivity<ActivityLicensesBinding>(ActivityLicensesBinding::inflate) {

	override fun setupView() {
		setupToolbar()
	}

	private fun setupToolbar() {
		binding.mtToolbar.toolbar.setTitle(R.string.screen_licenses_title)
		setSupportActionBar(binding.mtToolbar.toolbar)
	}

}
