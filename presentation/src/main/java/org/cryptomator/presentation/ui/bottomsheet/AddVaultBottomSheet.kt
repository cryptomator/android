package org.cryptomator.presentation.ui.bottomsheet

import kotlinx.android.synthetic.main.dialog_bottom_sheet_add_vault.*
import org.cryptomator.generator.BottomSheet
import org.cryptomator.presentation.R

@BottomSheet(R.layout.dialog_bottom_sheet_add_vault)
class AddVaultBottomSheet : BaseBottomSheet<AddVaultBottomSheet.Callback>() {

	interface Callback {
		fun onCreateVault()
		fun onAddExistingVault()
	}

	override fun setupView() {
		title.text = getString(R.string.screen_vault_list_actions_title)
		create_new_vault.setOnClickListener { callback?.onCreateVault() }
		add_existing_vault.setOnClickListener { callback?.onAddExistingVault() }
	}
}
