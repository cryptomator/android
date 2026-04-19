package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.DialogTrialExpiredBinding

@Dialog
class TrialExpiredDialog : BaseDialog<TrialExpiredDialog.Callback, DialogTrialExpiredBinding>(DialogTrialExpiredBinding::inflate) {

	interface Callback {

		/**
 * Called when the user chooses to unlock the full version from the trial-expired dialog.
 */
fun onUnlockFullVersionClicked()
	}

	/**
	 * Configures the dialog shown when the trial has expired and creates it.
	 *
	 * The dialog is given a title and two buttons: a positive "unlock" button that notifies
	 * the callback via `onUnlockFullVersionClicked()`, and a negative "continue read-only"
	 * button that dismisses the dialog without further action.
	 *
	 * @param builder The AlertDialog.Builder used to configure the dialog.
	 * @return The created Dialog instance.
	 */
	public override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		builder //
			.setTitle(R.string.dialog_trial_expired_title) //
			.setPositiveButton(getString(R.string.dialog_trial_expired_unlock)) { _: DialogInterface, _: Int -> callback?.onUnlockFullVersionClicked() } //
			.setNegativeButton(getString(R.string.dialog_trial_expired_continue_read_only)) { _: DialogInterface, _: Int -> }
		return builder.create()
	}

	/**
	 * Performs view-specific setup for the dialog.
	 *
	 * This implementation performs no additional initialization.
	 */
	public override fun setupView() {
		// empty
	}

	companion object {

		/**
		 * Create a new TrialExpiredDialog instance.
		 *
		 * @return A DialogFragment that is a new TrialExpiredDialog instance.
		 */
		fun newInstance(): DialogFragment {
			return TrialExpiredDialog()
		}
	}
}
