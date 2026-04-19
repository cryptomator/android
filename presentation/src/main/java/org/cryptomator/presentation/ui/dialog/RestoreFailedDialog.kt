package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import android.view.KeyEvent
import androidx.appcompat.app.AlertDialog
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.DialogRestoreFailedBinding

@Dialog
class RestoreFailedDialog : BaseDialog<RestoreFailedDialog.Callback, DialogRestoreFailedBinding>(DialogRestoreFailedBinding::inflate) {

	interface Callback {

		/**
 * Called when the restore-failed dialog has been dismissed or finished by the user.
 */
fun onRestoreFailedDialogFinished()
	}

	/**
	 * Configures the provided AlertDialog.Builder with the restore-failed title, a neutral button
	 * that notifies the callback when pressed, and a BACK-key handler that dismisses the dialog
	 * and notifies the callback.
	 *
	 * @return The created AlertDialog instance.
	 */
	public override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		builder //
			.setTitle(R.string.dialog_restore_failed_title) //
			.setNeutralButton(getString(R.string.dialog_restore_failed_positive_button)) { _: DialogInterface, _: Int -> callback?.onRestoreFailedDialogFinished() }
			.setOnKeyListener { _, keyCode, _ ->
				if (keyCode == KeyEvent.KEYCODE_BACK) {
					dialog?.dismiss()
					callback?.onRestoreFailedDialogFinished()
					true
				} else {
					false
				}
			}
		return builder.create()
	}

	/**
	 * Ensures the dialog cannot be dismissed by touching outside when the view is started.
	 */
	public override fun setupView() {
		super.onStart()
		val dialog = dialog as AlertDialog?
		dialog?.setCanceledOnTouchOutside(false)
	}

	companion object {

		/**
		 * Creates a new RestoreFailedDialog instance.
		 *
		 * @return A new RestoreFailedDialog instance.
		 */
		fun newInstance(): RestoreFailedDialog {
			return RestoreFailedDialog()
		}
	}
}
