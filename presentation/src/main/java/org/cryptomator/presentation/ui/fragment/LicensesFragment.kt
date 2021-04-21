package org.cryptomator.presentation.ui.fragment

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import org.cryptomator.presentation.R

// Don't delete this file as it isn't unused but referenced by layout file
class LicensesFragment : PreferenceFragmentCompat() {

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(R.xml.licenses)
	}
}
