package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import kotlinx.android.synthetic.main.dialog_no_screen_lock_set.cb_select_screen_lock

@Dialog(R.layout.dialog_no_screen_lock_set)
class AskForLockScreenDialog : BaseDialog<AskForLockScreenDialog.Callback>() {

	interface Callback {

		fun onAskForLockScreenFinished(setScreenLock: Boolean)
	}

	override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		return builder //
			.setTitle(R.string.dialog_no_screen_lock_title) //
			.setNeutralButton(getString(R.string.dialog_no_screen_lock_neutral_button)) { _: DialogInterface, _: Int -> callback?.onAskForLockScreenFinished(cb_select_screen_lock.isChecked) } //
			.create()
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
