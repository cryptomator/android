package org.cryptomator.presentation.ui.dialog

import androidx.appcompat.app.AlertDialog
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.DialogNoFullVersionBinding

@Dialog
class NoFullVersionDialog : BaseInformationalDialog<DialogNoFullVersionBinding>(DialogNoFullVersionBinding::inflate) {

	override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog = builder
		.setTitle(R.string.dialog_no_full_version_title)
		.setNeutralButton(getString(R.string.dialog_no_full_version_positive_button)) { _, _ -> }
		.dismissOnBackKey()
		.create()

	companion object {
		fun newInstance() = NoFullVersionDialog()
	}
}
