package org.cryptomator.presentation.ui.dialog

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.DialogAppUpdateBinding
import org.cryptomator.presentation.databinding.ViewDialogErrorBinding

@Dialog
class UpdateAppDialog : BaseProgressErrorDialog<UpdateAppDialog.Callback, DialogAppUpdateBinding>(DialogAppUpdateBinding::inflate) {

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
		binding.tvMessage.setText(R.string.dialog_download_update_message)
		binding.tvMessage.requestFocus()
	}

	override fun dialogProgressLayout(): LinearLayout {
		return binding.llDialogProgress.llProgress
	}

	override fun dialogProgressTextView(): TextView {
		return binding.llDialogProgress.tvProgress
	}

	override fun dialogErrorBinding(): ViewDialogErrorBinding {
		return binding.llDialogError
	}

	override fun enableViewAfterError(): View {
		return binding.tvMessage
	}

	companion object {

		fun newInstance(): UpdateAppDialog {
			return UpdateAppDialog()
		}
	}
}
