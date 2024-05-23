package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.DialogNoScreenLockSetBinding

@Dialog
class AskForLockScreenDialog : BaseDialog<AskForLockScreenDialog.Callback, DialogNoScreenLockSetBinding>(DialogNoScreenLockSetBinding::inflate) {

	interface Callback {

		fun onAskForLockScreenFinished(setScreenLock: Boolean)
	}

	override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		builder //
			.setTitle(R.string.dialog_no_screen_lock_title) //
			.setNeutralButton(getString(R.string.dialog_unable_to_share_positive_button)) { _: DialogInterface, _: Int -> callback?.onAskForLockScreenFinished(binding.cbSelectScreenLock.isChecked) }
		return builder.create()
	}

	public override fun setupView() {
		// empty
	}

	companion object {

		fun newInstance(): DialogFragment {
			return AskForLockScreenDialog()
		}
	}
}
