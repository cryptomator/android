package org.cryptomator.presentation.ui.dialog

import androidx.appcompat.app.AlertDialog
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.DialogRestoreFailedBinding

@Dialog
class RestoreFailedDialog : BaseInformationalDialog<DialogRestoreFailedBinding>(DialogRestoreFailedBinding::inflate) {

	override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog = builder
		.setTitle(R.string.dialog_restore_failed_title)
		.setNeutralButton(getString(R.string.dialog_restore_failed_positive_button)) { _, _ -> }
		.dismissOnBackKey()
		.create()

	companion object {
		fun newInstance() = RestoreFailedDialog()
	}
}
