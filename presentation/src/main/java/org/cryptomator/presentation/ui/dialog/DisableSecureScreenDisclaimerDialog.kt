package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import android.text.method.LinkMovementMethod
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import kotlinx.android.synthetic.main.dialog_disable_secure_screen_disclaimer.tv_disable_secure_screen_disclaimer

@Dialog(R.layout.dialog_disable_secure_screen_disclaimer)
class DisableSecureScreenDisclaimerDialog : BaseDialog<DisableSecureScreenDisclaimerDialog.Callback>() {

	interface Callback {

		fun onDisableSecureScreenDisclaimerAccepted()
		fun onDisableSecureScreenDisclaimerRejected()
	}

	public override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		builder //
				.setTitle(R.string.dialog_disable_app_obscured_disclaimer_title) //
				.setPositiveButton(getString(R.string.dialog_disable_app_obscured_positive_button)) { _: DialogInterface, _: Int -> callback?.onDisableSecureScreenDisclaimerAccepted() } //
				.setNegativeButton(getString(R.string.dialog_disable_app_obscured_negative_button)) { _: DialogInterface, _: Int -> callback?.onDisableSecureScreenDisclaimerRejected() }
		return builder.create()
	}

	public override fun setupView() {
		tv_disable_secure_screen_disclaimer.movementMethod = LinkMovementMethod.getInstance()
	}

	override fun onCancel(dialog: DialogInterface) {
		super.onCancel(dialog)
		callback?.onDisableSecureScreenDisclaimerRejected()
	}

	companion object {

		fun newInstance(): DialogFragment {
			return DisableSecureScreenDisclaimerDialog()
		}
	}
}
