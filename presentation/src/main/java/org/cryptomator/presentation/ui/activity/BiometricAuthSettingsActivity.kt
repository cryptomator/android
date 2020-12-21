package org.cryptomator.presentation.ui.activity

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricManager
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.toolbar_layout.*
import org.cryptomator.domain.Vault
import org.cryptomator.generator.Activity
import org.cryptomator.presentation.R
import org.cryptomator.presentation.model.VaultModel
import org.cryptomator.presentation.presenter.BiometricAuthSettingsPresenter
import org.cryptomator.presentation.ui.activity.view.BiometricAuthSettingsView
import org.cryptomator.presentation.ui.dialog.BiometricAuthKeyInvalidatedDialog
import org.cryptomator.presentation.ui.dialog.EnrollSystemBiometricDialog
import org.cryptomator.presentation.ui.dialog.EnterPasswordDialog
import org.cryptomator.presentation.ui.fragment.BiometricAuthSettingsFragment
import org.cryptomator.presentation.util.BiometricAuthentication
import javax.inject.Inject

@Activity
class BiometricAuthSettingsActivity : BaseActivity(), //
		EnterPasswordDialog.Callback, //
		BiometricAuthSettingsView, //
		BiometricAuthentication.Callback, //
		EnrollSystemBiometricDialog.Callback {

	@Inject
	lateinit var presenter: BiometricAuthSettingsPresenter

	override fun setupView() {
		toolbar.setTitle(R.string.screen_settings_biometric_auth)
		setSupportActionBar(toolbar)

		showSetupBiometricAuthDialog()
	}

	override fun showSetupBiometricAuthDialog() {
		val biometricAuthenticationAvailable = BiometricManager.from(context()).canAuthenticate()
		if (biometricAuthenticationAvailable == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
			showDialog(EnrollSystemBiometricDialog.newInstance())
		}
	}

	override fun showBiometricAuthKeyInvalidatedDialog() {
		showDialog(BiometricAuthKeyInvalidatedDialog.newInstance())
	}

	override fun createFragment(): Fragment? = BiometricAuthSettingsFragment()

	override fun renderVaultList(vaultModelCollection: List<VaultModel>) {
		biometricAuthSettingsFragment().showVaults(vaultModelCollection)
	}

	override fun clearVaultList() {
		biometricAuthSettingsFragment().clearVaultList()
	}

	override fun showEnterPasswordDialog(vaultModel: VaultModel) {
		showDialog(EnterPasswordDialog.newInstance(vaultModel))
	}

	override fun onUnlockClick(vaultModel: VaultModel, password: String) {
		val vaultModelWithSavedPassword = VaultModel( //
				Vault //
						.aCopyOf(vaultModel.toVault()) //
						.withSavedPassword(password) //
						.build())

		presenter.verifyPassword(vaultModelWithSavedPassword)
	}

	override fun onUnlockCanceled() {
		presenter.onUnlockCanceled()
	}

	@RequiresApi(api = Build.VERSION_CODES.M)
	override fun showBiometricAuthenticationDialog(vaultModel: VaultModel) {
		BiometricAuthentication(this, context(), BiometricAuthentication.CryptoMode.ENCRYPT, presenter.useConfirmationInFaceUnlockBiometricAuthentication())
				.startListening(biometricAuthSettingsFragment(), vaultModel)
	}

	private fun biometricAuthSettingsFragment(): BiometricAuthSettingsFragment = getCurrentFragment(R.id.fragmentContainer) as BiometricAuthSettingsFragment

	override fun onSetupBiometricAuthInSystemClicked() {
		presenter.onSetupBiometricAuthInSystemClicked()
	}

	override fun onCancelSetupBiometricAuthInSystemClicked() {
		finish()
	}

	override fun onBiometricAuthenticated(vault: VaultModel) {
		presenter.saveVault(vault.toVault())
	}

	override fun onBiometricAuthenticationFailed(vault: VaultModel) {
		showError(getString(R.string.error_biometric_auth_aborted))
		biometricAuthSettingsFragment().addOrUpdateVault(vault)
	}

	override fun onBiometricKeyInvalidated(vault: VaultModel) {
		presenter.onBiometricAuthKeyInvalidated(vault)
	}
}
