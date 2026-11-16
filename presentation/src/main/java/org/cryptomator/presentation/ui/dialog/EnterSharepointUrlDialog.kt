package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.DialogEnterSharepointUrlBinding
import org.cryptomator.presentation.util.SharepointUrlParser

@Dialog
class EnterSharepointUrlDialog : BaseDialog<EnterSharepointUrlDialog.Callback, DialogEnterSharepointUrlBinding>(DialogEnterSharepointUrlBinding::inflate) {

	interface Callback {

		fun onSharepointUrlEntered(siteUrl: String)
	}

	override fun onStart() {
		super.onStart()
		val dialog = dialog as AlertDialog?
		dialog?.let {
			dialog.getButton(android.app.Dialog.BUTTON_POSITIVE).setOnClickListener {
				val siteUrl = SharepointUrlParser.siteUrlOf(binding.etSharepointUrl.text.toString())
				if (siteUrl != null) {
					callback?.onSharepointUrlEntered(siteUrl)
					dismiss()
				} else {
					binding.tilSharepointUrl.error = getString(R.string.dialog_enter_sharepoint_url_invalid)
				}
			}
			dialog.setCanceledOnTouchOutside(false)
			binding.etSharepointUrl.requestFocus()
			binding.etSharepointUrl.setSelection(binding.etSharepointUrl.length())
		}
	}

	override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		return builder.setTitle(R.string.dialog_enter_sharepoint_url_title)
			.setPositiveButton(R.string.dialog_enter_sharepoint_url_positive_button) { _: DialogInterface, _: Int -> }
			.setNegativeButton(R.string.dialog_enter_sharepoint_url_negative_button) { _: DialogInterface, _: Int -> }
			.create()
	}

	override fun setupView() {
		registerOnEditorDoneActionAndPerformButtonClick(binding.etSharepointUrl) { (dialog as AlertDialog).getButton(android.app.Dialog.BUTTON_POSITIVE) }
		binding.etSharepointUrl.doAfterTextChanged { binding.tilSharepointUrl.error = null }
		dialog?.let { showKeyboard(it) }
	}

	companion object {

		fun newInstance(): EnterSharepointUrlDialog {
			return EnterSharepointUrlDialog()
		}
	}
}
