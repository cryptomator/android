package org.cryptomator.presentation.ui.activity.view

import org.cryptomator.presentation.model.CloudFolderModel
import org.cryptomator.presentation.model.VaultModel

interface AutoUploadChooseVaultView : View {

	fun displayDialogUnableToUploadFiles()
	fun displayVaults(vaults: List<VaultModel>)
	fun showChosenLocation(location: CloudFolderModel)
	fun showEnterPasswordDialog(vaultModel: VaultModel)
	fun showBiometricAuthKeyInvalidatedDialog()

}
