package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R

@Dialog(R.layout.dialog_debug_mode_disclaimer)
class DebugModeDisclaimerDialog : BaseDialog<DebugModeDisclaimerDialog.Callback>() {

	interface Callback {

		fun onDisclaimerAccepted()
		fun onDisclaimerRejected()
	}

	public override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		builder //
			.setTitle(R.string.dialog_debug_mode_disclaimer_title) //
			.setPositiveButton(getString(R.string.dialog_debug_mode_positive_button)) { _: DialogInterface, _: Int -> callback?.onDisclaimerAccepted() } //
			.setNegativeButton(getString(R.string.dialog_debug_mode_negative_button)) { _: DialogInterface, _: Int -> callback?.onDisclaimerRejected() }
		return builder.create()
	}

	public override fun setupView() {
		// empty
	}

	override fun onCancel(dialog: DialogInterface) {
		super.onCancel(dialog)
		callback?.onDisclaimerRejected()
	}

	companion object {

		fun newInstance(): DialogFragment {
			return DebugModeDisclaimerDialog()
		}
	}
}
