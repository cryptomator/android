package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import android.view.KeyEvent
import androidx.appcompat.app.AlertDialog
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.DialogNoFullVersionBinding

@Dialog
class NoFullVersionDialog : BaseDialog<NoFullVersionDialog.Callback, DialogNoFullVersionBinding>(DialogNoFullVersionBinding::inflate) {

	interface Callback {

		/**
 * Notifies that the dialog was dismissed or finished by the user.
 *
 * Invoked when the dialog is closed via supported actions (for example the neutral button or the back key).
 */
fun onNoFullVersionDialogFinished()
	}

	/**
	 * Configures the provided AlertDialog.Builder for the "no full version" dialog and creates the dialog.
	 *
	 * Sets the dialog title, adds a neutral button that notifies the dialog callback when pressed, and
	 * intercepts the BACK key to dismiss the dialog and notify the same callback.
	 *
	 * @param builder The AlertDialog.Builder to configure.
	 * @return The created AlertDialog instance.
	 */
	public override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		builder //
			.setTitle(R.string.dialog_no_full_version_title) //
			.setNeutralButton(getString(R.string.dialog_no_full_version_positive_button)) { _: DialogInterface, _: Int -> callback?.onNoFullVersionDialogFinished() }
			.setOnKeyListener { _, keyCode, _ ->
				if (keyCode == KeyEvent.KEYCODE_BACK) {
					dialog?.dismiss()
					callback?.onNoFullVersionDialogFinished()
					true
				} else {
					false
				}
			}
		return builder.create()
	}

	/**
	 * Prepares the dialog when it becomes visible and prevents cancellation by touching outside its window.
	 */
	public override fun setupView() {
		super.onStart()
		val dialog = dialog as AlertDialog?
		dialog?.setCanceledOnTouchOutside(false)
	}

	companion object {

		/**
		 * Creates a new NoFullVersionDialog.
		 *
		 * @return A new NoFullVersionDialog instance.
		 */
		fun newInstance(): NoFullVersionDialog {
			return NoFullVersionDialog()
		}
	}
}
