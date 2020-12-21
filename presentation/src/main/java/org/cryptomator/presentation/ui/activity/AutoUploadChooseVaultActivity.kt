package org.cryptomator.presentation.ui.activity

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.toolbar_layout.*
import org.cryptomator.generator.Activity
import org.cryptomator.presentation.R
import org.cryptomator.presentation.model.CloudFolderModel
import org.cryptomator.presentation.model.VaultModel
import org.cryptomator.presentation.presenter.AutoUploadChooseVaultPresenter
import org.cryptomator.presentation.ui.activity.view.AutoUploadChooseVaultView
import org.cryptomator.presentation.ui.dialog.BiometricAuthKeyInvalidatedDialog
import org.cryptomator.presentation.ui.dialog.EnterPasswordDialog
import org.cryptomator.presentation.ui.dialog.NotEnoughVaultsDialog
import org.cryptomator.presentation.ui.fragment.AutoUploadChooseVaultFragment
import org.cryptomator.presentation.util.BiometricAuthentication
import javax.inject.Inject

@Activity
class AutoUploadChooseVaultActivity : BaseActivity(), //
		AutoUploadChooseVaultView, //
		NotEnoughVaultsDialog.Callback, //
		EnterPasswordDialog.Callback,
		BiometricAuthentication.Callback {

	@Inject
	lateinit var presenter: AutoUploadChooseVaultPresenter

	override fun setupView() {
		setupToolbar()
	}

	private fun setupToolbar() {
		toolbar.title = getString(R.string.screen_settings_auto_photo_upload_title)
		setSupportActionBar(toolbar)
		supportActionBar?.let {
			it.setDisplayHomeAsUpEnabled(true)
			it.setHomeAsUpIndicator(R.drawable.ic_clear)
		}
	}

	override fun createFragment(): Fragment? = AutoUploadChooseVaultFragment()


	override fun displayVaults(vaults: List<VaultModel>) {
		autoUploadChooseVaultFragment().displayVaults(vaults)
	}

	override fun displayDialogUnableToUploadFiles() {
		NotEnoughVaultsDialog //
				.withContext(this) //
				.andTitle(getString(R.string.dialog_unable_to_auto_upload_files_title)) //
				.show()
	}

	override fun onNotEnoughVaultsOkClicked() {
		finish()
	}

	override fun onNotEnoughVaultsCreateVaultClicked() {
		// FIXME #202: vault list activity is twice on the stack
		val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
		launchIntent?.let { startActivity(it) }
		finish()
	}

	override fun showChosenLocation(location: CloudFolderModel) {
		autoUploadChooseVaultFragment().showChosenLocation(location)
	}

	override fun onUnlockCanceled() {
		presenter.onUnlockCanceled()
	}

	override fun onUnlockClick(vaultModel: VaultModel, password: String) {
		presenter.onUnlockPressed(vaultModel, password)
	}

	@RequiresApi(api = Build.VERSION_CODES.M)
	override fun showEnterPasswordDialog(vaultModel: VaultModel) {
		if (vaultWithBiometricAuthEnabled(vaultModel)) {
			BiometricAuthentication(this, context(), BiometricAuthentication.CryptoMode.DECRYPT, presenter.useConfirmationInFaceUnlockBiometricAuthentication())
					.startListening(autoUploadChooseVaultFragment(), vaultModel)
		} else {
			showDialog(EnterPasswordDialog.newInstance(vaultModel))
		}
	}

	override fun onBiometricAuthenticated(vault: VaultModel) {
		presenter.onUnlockPressed(vault, vault.password)
	}

	override fun onBiometricAuthenticationFailed(vault: VaultModel) {
		showDialog(EnterPasswordDialog.newInstance(vault))
	}

	override fun onBiometricKeyInvalidated(vault: VaultModel) {
		presenter.onBiometricKeyInvalidated(vault)
	}

	override fun showBiometricAuthKeyInvalidatedDialog() {
		showDialog(BiometricAuthKeyInvalidatedDialog.newInstance())
	}

	private fun vaultWithBiometricAuthEnabled(vault: VaultModel): Boolean = vault.password != null

	private fun autoUploadChooseVaultFragment(): AutoUploadChooseVaultFragment = getCurrentFragment(R.id.fragmentContainer) as AutoUploadChooseVaultFragment
}
