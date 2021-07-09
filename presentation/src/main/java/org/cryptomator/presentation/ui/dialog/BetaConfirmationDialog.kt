package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R

@Dialog(R.layout.dialog_beta_confirmation)
class BetaConfirmationDialog : BaseDialog<BetaConfirmationDialog.Callback>() {

	interface Callback {

		fun onAskForBetaConfirmationFinished()
	}

	public override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		builder //
			.setTitle(R.string.dialog_beta_confirmation_title) //
			.setNeutralButton(getString(R.string.dialog_beta_confirmation_neutral_button)) { _: DialogInterface, _: Int -> callback?.onAskForBetaConfirmationFinished() }
		return builder.create()
	}

	public override fun setupView() {
		// empty
	}

	companion object {

		fun newInstance(): DialogFragment {
			return BetaConfirmationDialog()
		}
	}
}
