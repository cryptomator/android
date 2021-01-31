package org.cryptomator.presentation.ui.activity.view

import org.cryptomator.presentation.model.CloudFolderModel
import org.cryptomator.presentation.model.VaultModel

interface VaultListView : View {

	fun renderVaultList(vaultModelCollection: List<VaultModel>)
	fun showVaultCreationHint()
	fun hideVaultCreationHint()
	fun deleteVaultFromAdapter(vaultId: Long)
	fun addOrUpdateVault(vault: VaultModel)
	fun renameVault(vaultModel: VaultModel)
	fun navigateToVaultContent(vault: VaultModel, decryptedRoot: CloudFolderModel)
	fun showEnterPasswordDialog(vault: VaultModel)
	fun showBiometricDialog(vault: VaultModel)
	fun showChangePasswordDialog(vaultModel: VaultModel)
	fun getEncryptedPasswordWithBiometricAuthentication(vaultModel: VaultModel)
	fun showVaultSettingsDialog(vaultModel: VaultModel)
	fun showAddVaultBottomSheet()
	fun showRenameDialog(vaultModel: VaultModel)
	fun showBiometricAuthKeyInvalidatedDialog()
	fun isVaultLocked(vaultModel: VaultModel): Boolean
	fun cancelBasicAuthIfRunning()
	fun stoppedBiometricAuthDuringCloudAuthentication(): Boolean
	fun vaultMoved(vaults: List<VaultModel>)
	fun rowMoved(fromPosition: Int, toPosition: Int)

}
