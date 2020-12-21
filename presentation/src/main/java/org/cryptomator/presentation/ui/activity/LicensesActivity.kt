package org.cryptomator.presentation.ui.activity

import kotlinx.android.synthetic.main.toolbar_layout.*
import org.cryptomator.generator.Activity
import org.cryptomator.presentation.R

@Activity(layout = R.layout.activity_licenses)
class LicensesActivity : BaseActivity() {

	override fun setupView() {
		setupToolbar()
	}

	private fun setupToolbar() {
		toolbar.setTitle(R.string.screen_licenses_title)
		setSupportActionBar(toolbar)
	}

}
