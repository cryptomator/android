package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R

@Dialog(R.layout.dialog_biometric_auth_key_invalidated)
class BiometricAuthKeyInvalidatedDialog : BaseDialog<BiometricAuthKeyInvalidatedDialog>() {

	public override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		builder //
				.setTitle(R.string.dialog_biometric_auth_key_invalidated_title) //
				.setNegativeButton(getString(R.string.dialog_biometric_auth_key_invalidated_neutral_button)) { _: DialogInterface, _: Int -> }
		return builder.create()
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
