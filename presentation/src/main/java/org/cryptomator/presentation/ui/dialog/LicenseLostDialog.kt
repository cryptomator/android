package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.DialogLicenseLostBinding

@Dialog
class LicenseLostDialog : BaseDialog<LicenseLostDialog.Callback, DialogLicenseLostBinding>(DialogLicenseLostBinding::inflate) {

	interface Callback {

		fun onReenterLicenseClicked()
	}

	public override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		builder //
			.setTitle(R.string.dialog_license_lost_title) //
			.setPositiveButton(getString(R.string.dialog_license_lost_reenter)) { _: DialogInterface, _: Int -> callback?.onReenterLicenseClicked() } //
			.setNegativeButton(getString(R.string.dialog_license_lost_later)) { _: DialogInterface, _: Int -> }
		return builder.create()
	}

	public override fun setupView() {
		val mail = requireArguments().getString(MAIL_ARG)
		binding.tvLicenseLostMessage.text = String.format(getString(R.string.dialog_license_lost_message), mail)
	}

	companion object {

		private const val MAIL_ARG = "mail"
		fun newInstance(mail: String): LicenseLostDialog {
			val dialog = LicenseLostDialog()
			val args = Bundle()
			args.putString(MAIL_ARG, mail)
			dialog.arguments = args
			return dialog
		}
	}
}
