package org.cryptomator.presentation.ui.dialog

import android.app.Activity
import android.content.DialogInterface
import android.text.method.LinkMovementMethod
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import kotlinx.android.synthetic.main.dialog_app_is_obscured_info.tv_app_is_obscured_info

@Dialog(R.layout.dialog_app_is_obscured_info)
class AppIsObscuredInfoDialog : BaseDialog<Activity>() {

	public override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		return builder //
			.setTitle(R.string.dialog_app_is_obscured_info_title) //
			.setNeutralButton(R.string.dialog_app_is_obscured_info_neutral_button) { dialog: DialogInterface, _: Int -> dialog.dismiss() } //
			.create()
	}

	override fun disableDialogWhenObscured(): Boolean {
		return false
	}

	public override fun setupView() {
		tv_app_is_obscured_info.movementMethod = LinkMovementMethod.getInstance()
	}

	companion object {

		fun newInstance(): DialogFragment {
			return AppIsObscuredInfoDialog()
		}
	}
}
