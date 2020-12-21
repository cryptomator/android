package org.cryptomator.presentation.ui.activity.view

import org.cryptomator.presentation.model.VaultModel

interface BiometricAuthSettingsView : View {

	fun renderVaultList(vaultModelCollection: List<VaultModel>)
	fun clearVaultList()
	fun showBiometricAuthenticationDialog(vaultModel: VaultModel)
	fun showEnterPasswordDialog(vaultModel: VaultModel)
	fun showSetupBiometricAuthDialog()
	fun showBiometricAuthKeyInvalidatedDialog()

}
