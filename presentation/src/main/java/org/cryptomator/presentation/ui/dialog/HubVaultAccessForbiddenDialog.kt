package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import android.view.KeyEvent
import androidx.appcompat.app.AlertDialog
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.DialogHubVaultAccessForbiddenBinding

@Dialog
class HubVaultAccessForbiddenDialog : BaseDialog<HubVaultAccessForbiddenDialog.Callback, DialogHubVaultAccessForbiddenBinding>(DialogHubVaultAccessForbiddenBinding::inflate) {

	interface Callback {

		fun onVaultAccessForbiddenDialogFinished()
	}

	public override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		builder //
			.setTitle(R.string.dialog_hub_vault_access_forbidden_title) //
			.setNeutralButton(getString(R.string.dialog_hub_vault_access_forbidden_neutral_button)) { _: DialogInterface, _: Int -> callback?.onVaultAccessForbiddenDialogFinished() }			.setOnKeyListener { _, keyCode, _ ->
				if (keyCode == KeyEvent.KEYCODE_BACK) {
					dialog?.dismiss()
					callback?.onVaultAccessForbiddenDialogFinished()
					true
				} else {
					false
				}
			}
		return builder.create()
	}

	public override fun setupView() {
	}

	companion object {

		fun newInstance(): HubVaultAccessForbiddenDialog {
			return HubVaultAccessForbiddenDialog()
		}
	}
}
