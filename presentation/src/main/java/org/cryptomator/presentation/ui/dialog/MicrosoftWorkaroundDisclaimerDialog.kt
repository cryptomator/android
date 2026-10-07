package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.DialogMicrosoftWorkaroundDisclaimerBinding

@Dialog
class MicrosoftWorkaroundDisclaimerDialog : BaseDialog<MicrosoftWorkaroundDisclaimerDialog.Callback, DialogMicrosoftWorkaroundDisclaimerBinding>(DialogMicrosoftWorkaroundDisclaimerBinding::inflate) {

	interface Callback {

		fun onMicrosoftDisclaimerAccepted()
		fun onMicrosoftDisclaimerRejected()
	}

	public override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		builder //
			.setTitle(R.string.dialog_microsoft_workaround_disclaimer_title) //
			.setPositiveButton(getString(R.string.dialog_microsoft_workaround_positive_button)) { _: DialogInterface, _: Int -> callback?.onMicrosoftDisclaimerAccepted() } //
			.setNegativeButton(getString(R.string.dialog_microsoft_workaround_negative_button)) { _: DialogInterface, _: Int -> callback?.onMicrosoftDisclaimerRejected() }
		return builder.create()
	}

	public override fun setupView() {
		// empty
	}

	override fun onCancel(dialog: DialogInterface) {
		super.onCancel(dialog)
		callback?.onMicrosoftDisclaimerRejected()
	}

	companion object {

		fun newInstance(): DialogFragment {
			return MicrosoftWorkaroundDisclaimerDialog()
		}
	}
}
