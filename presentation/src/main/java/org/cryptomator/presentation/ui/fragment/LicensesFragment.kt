package org.cryptomator.presentation.ui.fragment

import android.os.Bundle
import androidx.preference.PreferenceFragment

import org.cryptomator.presentation.R

class LicensesFragment : PreferenceFragment() {
	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(R.xml.licenses)
	}
}
