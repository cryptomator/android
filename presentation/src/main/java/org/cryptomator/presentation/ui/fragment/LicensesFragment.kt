package org.cryptomator.presentation.ui.fragment

import android.os.Bundle
import org.cryptomator.presentation.R
import org.cryptomator.presentation.ui.layout.PreferenceFragmentCompatLayout

// Don't delete this file as it isn't unused but referenced by layout file
class LicensesFragment : PreferenceFragmentCompatLayout() {

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(R.xml.licenses)
	}
}
