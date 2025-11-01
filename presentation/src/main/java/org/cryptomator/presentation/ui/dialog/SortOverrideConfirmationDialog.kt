package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.DialogSortOverrideConfirmationBinding
import org.cryptomator.presentation.model.VaultListSortOption

@Dialog
class SortOverrideConfirmationDialog :
	BaseDialog<SortOverrideConfirmationDialog.Callback, DialogSortOverrideConfirmationBinding>(DialogSortOverrideConfirmationBinding::inflate) {

	interface Callback {

		fun onSortOverrideConfirmed(sortOption: VaultListSortOption)
	}

	public override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		return builder //
			.setPositiveButton(getString(R.string.dialog_sort_override_positive_button)) { _: DialogInterface, _: Int -> callback?.onSortOverrideConfirmed(selectedSortOption()) } //
			.setNegativeButton(getString(R.string.dialog_sort_override_negative_button)) { _: DialogInterface, _: Int -> } //
			.create()
	}

	public override fun setupView() {
		binding.tvMessage.text = getString(R.string.dialog_sort_override_message)
		binding.rbSortByName.isChecked = true
	}

	private fun selectedSortOption(): VaultListSortOption {
		return when (binding.rgSortOptions.checkedRadioButtonId) {
			R.id.rb_sort_by_location -> VaultListSortOption.LOCATION
			else -> VaultListSortOption.NAME
		}
	}

	companion object {

		fun newInstance(): SortOverrideConfirmationDialog = SortOverrideConfirmationDialog()
	}
}
