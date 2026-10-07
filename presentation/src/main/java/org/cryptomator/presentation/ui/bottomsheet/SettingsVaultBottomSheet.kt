package org.cryptomator.presentation.ui.bottomsheet

import android.os.Bundle
import android.widget.LinearLayout
import org.cryptomator.generator.BottomSheet
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.DialogBottomSheetVaultSettingsBinding
import org.cryptomator.presentation.model.VaultModel

@BottomSheet(R.layout.dialog_bottom_sheet_vault_settings)
class SettingsVaultBottomSheet : BaseBottomSheet<SettingsVaultBottomSheet.Callback, DialogBottomSheetVaultSettingsBinding>(DialogBottomSheetVaultSettingsBinding::inflate) {

	interface Callback {

		fun onDeleteVaultClick(vaultModel: VaultModel)
		fun onRenameVaultClick(vaultModel: VaultModel)
		fun onLockVaultClick(vaultModel: VaultModel)
		fun onChangePasswordClick(vaultModel: VaultModel)
	}

	override fun setupView() {
		val vaultModel = requireArguments().getSerializable(VAULT_ARG) as VaultModel

		if (vaultModel.isLocked) {
			binding.lockVault.visibility = LinearLayout.GONE
		}
		val cloudType = vaultModel.cloudType
		binding.cloudImage.setImageResource(cloudType.vaultSelectedImageResource)
		binding.vaultName.text = vaultModel.name
		binding.vaultPath.text = vaultModel.path

		binding.etRename.setOnClickListener {
			callback?.onRenameVaultClick(vaultModel)
			dismiss()
		}
		binding.deleteVault.setOnClickListener {
			callback?.onDeleteVaultClick(vaultModel)
			dismiss()
		}
		binding.lockVault.setOnClickListener {
			callback?.onLockVaultClick(vaultModel)
			dismiss()
		}
		binding.changePassword.setOnClickListener {
			callback?.onChangePasswordClick(vaultModel)
			dismiss()
		}
	}

	companion object {

		private const val VAULT_ARG = "vault"
		fun newInstance(vaultModel: VaultModel): SettingsVaultBottomSheet {
			val dialog = SettingsVaultBottomSheet()
			val args = Bundle()
			args.putSerializable(VAULT_ARG, vaultModel)
			dialog.arguments = args
			return dialog
		}
	}
}
