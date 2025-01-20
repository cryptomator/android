package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import android.view.KeyEvent
import androidx.appcompat.app.AlertDialog
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.DialogBiometricAuthKeyInvalidatedBinding

@Dialog
class BiometricAuthKeyInvalidatedDialog : BaseDialog<BiometricAuthKeyInvalidatedDialog.Callback, DialogBiometricAuthKeyInvalidatedBinding>(DialogBiometricAuthKeyInvalidatedBinding::inflate) {

	interface Callback {

		fun onBiometricAuthKeyInvalidatedDialogFinished()

	}

	public override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		builder //
			.setTitle(R.string.dialog_biometric_auth_key_invalidated_title) //
			.setNegativeButton(getString(R.string.dialog_biometric_auth_key_invalidated_neutral_button)) { _: DialogInterface, _: Int -> }
			.setOnKeyListener { _, keyCode, _ ->
				if (keyCode == KeyEvent.KEYCODE_BACK) {
					dialog?.dismiss()
					callback?.onBiometricAuthKeyInvalidatedDialogFinished()
					true
				} else {
					false
				}
			}
		return builder.create()
	}

	override fun onStart() {
		super.onStart()
		val dialog = dialog as AlertDialog?
		dialog?.setCanceledOnTouchOutside(false)
	}

	public override fun setupView() {
		// empty
	}

	companion object {

		fun newInstance(): BiometricAuthKeyInvalidatedDialog {
			return BiometricAuthKeyInvalidatedDialog()
		}
	}
}
