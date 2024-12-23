package org.cryptomator.presentation.ui.activity

import android.os.Build
import androidx.annotation.RequiresApi
import org.cryptomator.domain.UnverifiedHubVaultConfig
import org.cryptomator.domain.UnverifiedVaultConfig
import org.cryptomator.domain.Vault
import org.cryptomator.generator.Activity
import org.cryptomator.generator.InjectIntent
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.ActivityUnlockVaultBinding
import org.cryptomator.presentation.intent.UnlockVaultIntent
import org.cryptomator.presentation.model.VaultModel
import org.cryptomator.presentation.presenter.UnlockVaultPresenter
import org.cryptomator.presentation.ui.activity.view.UnlockVaultView
import org.cryptomator.presentation.ui.dialog.BiometricAuthKeyInvalidatedDialog
import org.cryptomator.presentation.ui.dialog.ChangePasswordDialog
import org.cryptomator.presentation.ui.dialog.CreateHubDeviceDialog
import org.cryptomator.presentation.ui.dialog.EnterPasswordDialog
import org.cryptomator.presentation.ui.dialog.HubLicenseUpgradeRequiredDialog
import org.cryptomator.presentation.ui.dialog.HubUserSetupRequiredDialog
import org.cryptomator.presentation.ui.dialog.HubVaultAccessForbiddenDialog
import org.cryptomator.presentation.ui.dialog.HubVaultArchivedDialog
import org.cryptomator.presentation.ui.dialog.VaultNotFoundDialog
import org.cryptomator.presentation.ui.fragment.UnlockVaultFragment
import org.cryptomator.presentation.util.BiometricAuthentication
import javax.inject.Inject

@Activity
class UnlockVaultActivity : BaseActivity<ActivityUnlockVaultBinding>(ActivityUnlockVaultBinding::inflate), //
	UnlockVaultView, //
	EnterPasswordDialog.Callback, //
	BiometricAuthentication.Callback, //
	BiometricAuthKeyInvalidatedDialog.Callback, //
	ChangePasswordDialog.Callback, //
	VaultNotFoundDialog.Callback, //
	CreateHubDeviceDialog.Callback, //
	HubUserSetupRequiredDialog.Callback, //
	HubVaultArchivedDialog.Callback, //
	HubLicenseUpgradeRequiredDialog.Callback, //
	HubVaultAccessForbiddenDialog.Callback {

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
		val vaultWithoutPassword = Vault.aCopyOf(vault.toVault()).withSavedPassword(null, null).build()
		when (unlockVaultIntent.vaultAction()) {
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
		getCurrentFragment(R.id.fragment_container) as UnlockVaultFragment

	override fun showChangePasswordDialog(vaultModel: VaultModel, unverifiedVaultConfig: UnverifiedVaultConfig?) {
		showDialog(ChangePasswordDialog.newInstance(vaultModel, unverifiedVaultConfig))
	}

	override fun showCreateHubDeviceDialog(vaultModel: VaultModel, unverifiedVaultConfig: UnverifiedHubVaultConfig) {
		showDialog(CreateHubDeviceDialog.newInstance(vaultModel, unverifiedVaultConfig))
	}

	override fun showHubUserSetupRequiredDialog(unverifiedHubVaultConfig: UnverifiedHubVaultConfig) {
		showDialog(HubUserSetupRequiredDialog.newInstance(unverifiedHubVaultConfig))
	}

	override fun showHubLicenseUpgradeRequiredDialog() {
		showDialog(HubLicenseUpgradeRequiredDialog.newInstance())
	}

	override fun showHubVaultAccessForbiddenDialog() {
		showDialog(HubVaultAccessForbiddenDialog.newInstance())
	}

	override fun showHubVaultIsArchivedDialog() {
		showDialog(HubVaultArchivedDialog.newInstance())
	}

	override fun onChangePasswordClick(vaultModel: VaultModel, unverifiedVaultConfig: UnverifiedVaultConfig?, oldPassword: String, newPassword: String) {
		presenter.onChangePasswordClick(vaultModel, unverifiedVaultConfig, oldPassword, newPassword)
	}

	override fun onChangePasswordCanceled() {
		presenter.onChangePasswordCanceled()
	}

	override fun onDeleteMissingVaultClicked(vault: Vault) {
		presenter.onDeleteMissingVaultClicked(vault)
	}

	override fun onCancelMissingVaultClicked(vault: Vault) {
		presenter.onCancelMissingVaultClicked(vault)
	}

	override fun onCreateHubDeviceClicked(vaultModel: VaultModel, unverifiedVaultConfig: UnverifiedHubVaultConfig, deviceName: String, setupCode: String) {
		presenter.onCreateHubDeviceClick(vaultModel, unverifiedVaultConfig, deviceName, setupCode)
	}

	override fun onCreateHubDeviceCanceled() {
		finish()
	}

	override fun onGoToHubProfileClicked(unverifiedVaultConfig: UnverifiedHubVaultConfig) {
		presenter.onGoToHubProfileClicked(unverifiedVaultConfig)
	}

	override fun onCancelHubUserSetupClicked() {
		finish()
	}

	override fun onHubVaultArchivedDialogFinished() {
		finish()
	}

	override fun onHubLicenseUpgradeRequiredDialogFinished() {
		finish()
	}

	override fun onVaultAccessForbiddenDialogFinished() {
		finish()
	}

	override fun onBiometricAuthKeyInvalidatedDialogFinished() {
		finish()
	}

}
