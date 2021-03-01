package org.cryptomator.presentation.ui.bottomsheet

import org.cryptomator.generator.BottomSheet
import org.cryptomator.presentation.R
import kotlinx.android.synthetic.main.dialog_bottom_sheet_add_vault.add_existing_vault
import kotlinx.android.synthetic.main.dialog_bottom_sheet_add_vault.create_new_vault
import kotlinx.android.synthetic.main.dialog_bottom_sheet_add_vault.title

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
