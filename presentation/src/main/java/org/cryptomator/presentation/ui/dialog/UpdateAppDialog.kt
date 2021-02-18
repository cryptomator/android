package org.cryptomator.presentation.ui.dialog

import android.view.View
import androidx.appcompat.app.AlertDialog
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import kotlinx.android.synthetic.main.dialog_app_update.tv_message

@Dialog(R.layout.dialog_app_update)
class UpdateAppDialog : BaseProgressErrorDialog<UpdateAppDialog.Callback>() {

	interface Callback {

		fun onUpdateAppDialogLoaded()
	}

	override fun onStart() {
		super.onStart()
		callback?.onUpdateAppDialogLoaded()
	}

	public override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		return builder //
				.setTitle(getString(R.string.dialog_download_update_title)) //
				.setCancelable(false) //
				.create()
	}

	public override fun setupView() {
		tv_message.setText(R.string.dialog_download_update_message)
		tv_message.requestFocus()
	}

	override fun enableViewAfterError(): View {
		return tv_message
	}

	companion object {

		fun newInstance(): UpdateAppDialog {
			return UpdateAppDialog()
		}
	}
}
