package org.cryptomator.presentation.ui.activity.view

import org.cryptomator.presentation.model.VaultModel

interface BiometricAuthSettingsView : View {

	fun renderVaultList(vaultModelCollection: List<VaultModel>)
	fun clearVaultList()
	fun showSetupBiometricAuthDialog()

}
