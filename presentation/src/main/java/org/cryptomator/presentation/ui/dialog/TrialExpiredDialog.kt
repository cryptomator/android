package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.DialogTrialExpiredBinding

@Dialog
class TrialExpiredDialog : BaseDialog<TrialExpiredDialog.Callback, DialogTrialExpiredBinding>(DialogTrialExpiredBinding::inflate) {

	interface Callback {

		fun onUnlockFullVersionClicked()
	}

	public override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		builder //
			.setTitle(R.string.dialog_trial_expired_title) //
			.setPositiveButton(getString(R.string.dialog_trial_expired_unlock)) { _: DialogInterface, _: Int -> callback?.onUnlockFullVersionClicked() } //
			.setNegativeButton(getString(R.string.dialog_trial_expired_continue_read_only)) { _: DialogInterface, _: Int -> }
		return builder.create()
	}

	public override fun setupView() {
		// empty
	}

	companion object {

		fun newInstance(): TrialExpiredDialog {
			return TrialExpiredDialog()
		}
	}
}
