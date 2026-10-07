package org.cryptomator.presentation.ui.bottomsheet

import org.cryptomator.generator.BottomSheet
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.DialogBottomSheetAddVaultBinding

@BottomSheet(R.layout.dialog_bottom_sheet_add_vault)
class AddVaultBottomSheet : BaseBottomSheet<AddVaultBottomSheet.Callback, DialogBottomSheetAddVaultBinding>(DialogBottomSheetAddVaultBinding::inflate) {

	interface Callback {

		fun onCreateVault()
		fun onAddExistingVault()
	}

	override fun setupView() {
		binding.title.setText(R.string.screen_vault_list_actions_title)
		binding.createNewVault.setOnClickListener { callback?.onCreateVault() }
		binding.addExistingVault.setOnClickListener { callback?.onAddExistingVault() }
	}
}
