package org.cryptomator.presentation.ui.fragment

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import org.cryptomator.presentation.R

class LicensesFragment : PreferenceFragmentCompat() {

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(R.xml.licenses)
	}
}
