package org.cryptomator.presentation.ui.activity

import android.os.Build
import androidx.annotation.RequiresApi
import org.cryptomator.domain.UnverifiedVaultConfig
import org.cryptomator.domain.Vault
import org.cryptomator.generator.Activity
import org.cryptomator.generator.InjectIntent
import org.cryptomator.presentation.R
import org.cryptomator.presentation.intent.UnlockVaultIntent
import org.cryptomator.presentation.model.VaultModel
import org.cryptomator.presentation.presenter.UnlockVaultPresenter
import org.cryptomator.presentation.ui.activity.view.UnlockVaultView
import org.cryptomator.presentation.ui.dialog.BiometricAuthKeyInvalidatedDialog
import org.cryptomator.presentation.ui.dialog.ChangePasswordDialog
import org.cryptomator.presentation.ui.dialog.EnterPasswordDialog
import org.cryptomator.presentation.ui.dialog.VaultNotFoundDialog
import org.cryptomator.presentation.ui.fragment.UnlockVaultFragment
import org.cryptomator.presentation.util.BiometricAuthentication
import javax.inject.Inject

@Activity(layout = R.layout.activity_unlock_vault)
class UnlockVaultActivity : BaseActivity(), //
		UnlockVaultView, //
		BiometricAuthentication.Callback,
		ChangePasswordDialog.Callback,
		VaultNotFoundDialog.Callback {

	@Inject
	lateinit var presenter: UnlockVaultPresenter

	@InjectIntent
	lateinit var unlockVaultIntent: UnlockVaultIntent


	private lateinit var biometricAuthentication: BiometricAuthentication

	override fun finish() {
		super.finish()
		overridePendingTransition(0, 0)
	}

	override fun showEnterPasswordDialog(vault: VaultModel) {
		showDialog(EnterPasswordDialog.newInstance(vault))
	}

	@RequiresApi(api = Build.VERSION_CODES.M)
	override fun showBiometricDialog(vault: VaultModel) {
		biometricAuthentication = BiometricAuthentication(this, context(), BiometricAuthentication.CryptoMode.DECRYPT, presenter.useConfirmationInFaceUnlockBiometricAuthentication())
		biometricAuthentication.startListening(unlockVaultFragment(), vault)
	}

	@RequiresApi(api = Build.VERSION_CODES.M)
	override fun getEncryptedPasswordWithBiometricAuthentication(vaultModel: VaultModel) {
		biometricAuthentication = BiometricAuthentication(this, context(), BiometricAuthentication.CryptoMode.ENCRYPT, presenter.useConfirmationInFaceUnlockBiometricAuthentication())
		biometricAuthentication.startListening(unlockVaultFragment(), vaultModel)
	}

	override fun showBiometricAuthKeyInvalidatedDialog() {
		showDialog(BiometricAuthKeyInvalidatedDialog.newInstance())
	}

	@RequiresApi(api = Build.VERSION_CODES.M)
	override fun cancelBasicAuthIfRunning() {
		biometricAuthentication.stopListening()
	}

	@RequiresApi(api = Build.VERSION_CODES.M)
	override fun stoppedBiometricAuthDuringCloudAuthentication(): Boolean {
		return biometricAuthentication.stoppedBiometricAuthDuringCloudAuthentication()
	}

	override fun onUnlockClick(vaultModel: VaultModel, password: String) {
		presenter.onUnlockClick(vaultModel, password)
	}

	override fun onUnlockCanceled() {
		presenter.onUnlockCanceled()
	}

	override fun onBiometricAuthenticated(vault: VaultModel) {
		presenter.onBiometricAuthenticationSucceeded(vault)
	}

	override fun onBiometricAuthenticationFailed(vault: VaultModel) {
		val vaultWithoutPassword = Vault.aCopyOf(vault.toVault()).withSavedPassword(null).build()
		when(unlockVaultIntent.vaultAction()) {
			UnlockVaultIntent.VaultAction.CHANGE_PASSWORD -> presenter.saveVaultAfterChangePasswordButFailedBiometricAuth(vaultWithoutPassword)
			else -> {
				if (!presenter.startedUsingPrepareUnlock()) {
					presenter.startPrepareUnlockUseCase(vaultWithoutPassword)
				}
				showEnterPasswordDialog(VaultModel(vaultWithoutPassword))
			}
		}
	}

	override fun onBiometricKeyInvalidated(vault: VaultModel) {
		presenter.onBiometricKeyInvalidated()
	}

	private fun unlockVaultFragment(): UnlockVaultFragment = //
			getCurrentFragment(R.id.fragmentContainer) as UnlockVaultFragment

	override fun showChangePasswordDialog(vaultModel: VaultModel, unverifiedVaultConfig: UnverifiedVaultConfig?) {
		showDialog(ChangePasswordDialog.newInstance(vaultModel, unverifiedVaultConfig))
	}

	override fun onChangePasswordClick(vaultModel: VaultModel, unverifiedVaultConfig: UnverifiedVaultConfig?, oldPassword: String, newPassword: String) {
		presenter.onChangePasswordClick(vaultModel, unverifiedVaultConfig, oldPassword, newPassword)
	}

	override fun onDeleteMissingVaultClicked(vault: Vault) {
		presenter.onDeleteMissingVaultClicked(vault)
	}

	override fun onCancelMissingVaultClicked(vault: Vault) {
		presenter.onCancelMissingVaultClicked(vault)
	}

}
