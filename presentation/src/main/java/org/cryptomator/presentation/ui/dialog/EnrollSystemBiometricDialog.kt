package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R

@Dialog(R.layout.dialog_setup_biometric_auth_in_system)
class EnrollSystemBiometricDialog : BaseDialog<EnrollSystemBiometricDialog.Callback>() {

	interface Callback {
		fun onSetupBiometricAuthInSystemClicked()
		fun onCancelSetupBiometricAuthInSystemClicked()
	}

	override fun onStart() {
		super.onStart()
		allowClosingDialog(false)
	}

	override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		builder //
				.setTitle(getString(R.string.dialog_no_biometric_auth_set_up_title)) //
				.setPositiveButton(getString(R.string.dialog_unable_to_share_positive_button)  //
				) { _: DialogInterface, _: Int -> callback?.onSetupBiometricAuthInSystemClicked() }
				.setNegativeButton(getString(R.string.dialog_button_cancel)  //
				) { _: DialogInterface?, _: Int -> callback?.onCancelSetupBiometricAuthInSystemClicked() }
		return builder.create()
	}

	override fun setupView() {}

	companion object {
		fun newInstance(): EnrollSystemBiometricDialog {
			return EnrollSystemBiometricDialog()
		}
	}
}
