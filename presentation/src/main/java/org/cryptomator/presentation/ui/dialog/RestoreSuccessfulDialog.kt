package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import android.view.KeyEvent
import androidx.appcompat.app.AlertDialog
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.DialogRestoreSuccessfulBinding

@Dialog
class RestoreSuccessfulDialog : BaseDialog<RestoreSuccessfulDialog.Callback, DialogRestoreSuccessfulBinding>(DialogRestoreSuccessfulBinding::inflate) {

	interface Callback {

		/**
 * Invoked when the restore-successful dialog is finished by the user (for example via the dialog's neutral button or the back key).
 */
fun onRestoreSuccessfulDialogFinished()
	}

	/**
	 * Creates and configures the dialog shown after a successful restore.
	 *
	 * Configures the provided builder with a title, a neutral button that notifies the callback when tapped,
	 * and a key listener that dismisses the dialog and notifies the callback when the BACK key is pressed.
	 *
	 * @param builder The AlertDialog.Builder used to build the dialog.
	 * @return The created Dialog.
	 */
	public override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		builder //
			.setTitle(R.string.dialog_restore_successful_title) //
			.setNeutralButton(getString(R.string.dialog_restore_successful_positive_button)) { _: DialogInterface, _: Int -> callback?.onRestoreSuccessfulDialogFinished() }
			.setOnKeyListener { _, keyCode, _ ->
				if (keyCode == KeyEvent.KEYCODE_BACK) {
					dialog?.dismiss()
					callback?.onRestoreSuccessfulDialogFinished()
					true
				} else {
					false
				}
			}
		return builder.create()
	}

	/**
	 * Prevents the dialog from being canceled by touching outside its window.
	 *
	 * Applies this behavior to the underlying AlertDialog instance when present.
	 */
	public override fun setupView() {
		super.onStart()
		val dialog = dialog as AlertDialog?
		dialog?.setCanceledOnTouchOutside(false)
	}

	companion object {

		/**
		 * Creates a new RestoreSuccessfulDialog.
		 *
		 * @return A new RestoreSuccessfulDialog instance.
		 */
		fun newInstance(): RestoreSuccessfulDialog {
			return RestoreSuccessfulDialog()
		}
	}
}
