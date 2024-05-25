package org.cryptomator.presentation.ui.activity

import androidx.biometric.BiometricManager
import androidx.fragment.app.Fragment
import org.cryptomator.generator.Activity
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.ActivityLayoutBinding
import org.cryptomator.presentation.model.VaultModel
import org.cryptomator.presentation.presenter.BiometricAuthSettingsPresenter
import org.cryptomator.presentation.ui.activity.view.BiometricAuthSettingsView
import org.cryptomator.presentation.ui.dialog.EnrollSystemBiometricDialog
import org.cryptomator.presentation.ui.fragment.BiometricAuthSettingsFragment
import javax.inject.Inject

@Activity
class BiometricAuthSettingsActivity : BaseActivity<ActivityLayoutBinding>(ActivityLayoutBinding::inflate), //
	BiometricAuthSettingsView, //
	EnrollSystemBiometricDialog.Callback {

	@Inject
	lateinit var presenter: BiometricAuthSettingsPresenter

	override fun setupView() {
		binding.mtToolbar.toolbar.setTitle(R.string.screen_settings_biometric_auth)
		setSupportActionBar(binding.mtToolbar.toolbar)

		showSetupBiometricAuthDialog()
	}

	override fun showSetupBiometricAuthDialog() {
		val biometricAuthenticationAvailable = BiometricManager //
			.from(context()) //
			.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
		if (biometricAuthenticationAvailable == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
			showDialog(EnrollSystemBiometricDialog.newInstance())
		}
	}

	override fun createFragment(): Fragment = BiometricAuthSettingsFragment()

	override fun renderVaultList(vaultModelCollection: List<VaultModel>) {
		biometricAuthSettingsFragment().showVaults(vaultModelCollection)
	}

	override fun clearVaultList() {
		biometricAuthSettingsFragment().clearVaultList()
	}

	private fun biometricAuthSettingsFragment(): BiometricAuthSettingsFragment = getCurrentFragment(R.id.fragment_container) as BiometricAuthSettingsFragment

	override fun onSetupBiometricAuthInSystemClicked() {
		presenter.onSetupBiometricAuthInSystemClicked()
	}

	override fun onCancelSetupBiometricAuthInSystemClicked() {
		finish()
	}
}
