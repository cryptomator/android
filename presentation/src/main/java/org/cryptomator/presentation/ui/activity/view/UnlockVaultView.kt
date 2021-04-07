package org.cryptomator.presentation.ui.activity.view

import org.cryptomator.domain.UnverifiedVaultConfig
import org.cryptomator.presentation.model.VaultModel
import org.cryptomator.presentation.ui.dialog.EnterPasswordDialog

interface UnlockVaultView : View, EnterPasswordDialog.Callback {

	fun showEnterPasswordDialog(vault: VaultModel)
	fun showBiometricDialog(vault: VaultModel)
	fun getEncryptedPasswordWithBiometricAuthentication(vaultModel: VaultModel)
	fun showBiometricAuthKeyInvalidatedDialog()
	fun cancelBasicAuthIfRunning()
	fun stoppedBiometricAuthDuringCloudAuthentication(): Boolean
	fun showChangePasswordDialog(vaultModel: VaultModel, unverifiedVaultConfig: UnverifiedVaultConfig?)

}
