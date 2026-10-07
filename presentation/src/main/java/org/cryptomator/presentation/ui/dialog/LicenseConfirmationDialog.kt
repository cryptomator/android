package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.DialogLicenseConfirmationBinding

@Dialog
class LicenseConfirmationDialog : BaseDialog<LicenseConfirmationDialog.Callback, DialogLicenseConfirmationBinding>(DialogLicenseConfirmationBinding::inflate) {

	interface Callback {

		fun licenseConfirmationClicked()
	}

	public override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		return builder //
			.setTitle(getString(R.string.dialog_license_confirmation_title)) //
			.setNeutralButton(getText(R.string.dialog_license_confirmation_ok_btn)) { _: DialogInterface, _: Int -> callback?.licenseConfirmationClicked() } //
			.create()
	}

	public override fun setupView() {
		val mail = requireArguments().getSerializable(ARG_MAIL) as String
		binding.tvMessage.text = String.format(getString(R.string.dialog_license_confirmation_message), mail)
	}

	companion object {

		private const val ARG_MAIL = "argMail"
		fun newInstance(mail: String): LicenseConfirmationDialog {
			val confirmationDialog = LicenseConfirmationDialog()
			val args = Bundle()
			args.putSerializable(ARG_MAIL, mail)
			confirmationDialog.arguments = args
			return confirmationDialog
		}
	}
}
