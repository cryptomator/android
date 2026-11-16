package org.cryptomator.presentation.ui.dialog

import androidx.appcompat.app.AlertDialog
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.DialogRestoreSuccessfulBinding

@Dialog
class RestoreSuccessfulDialog : BaseInformationalDialog<DialogRestoreSuccessfulBinding>(DialogRestoreSuccessfulBinding::inflate) {

	override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog = builder
		.setTitle(R.string.dialog_restore_successful_title)
		.setNeutralButton(getString(R.string.dialog_restore_successful_positive_button)) { _, _ -> }
		.dismissOnBackKey()
		.create()

	companion object {
		fun newInstance() = RestoreSuccessfulDialog()
	}
}
