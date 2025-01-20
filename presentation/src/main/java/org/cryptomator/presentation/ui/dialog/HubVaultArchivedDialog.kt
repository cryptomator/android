package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import android.view.KeyEvent
import androidx.appcompat.app.AlertDialog
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.DialogHubVaultArchivedBinding

@Dialog
class HubVaultArchivedDialog : BaseDialog<HubVaultArchivedDialog.Callback, DialogHubVaultArchivedBinding>(DialogHubVaultArchivedBinding::inflate) {

	interface Callback {

		fun onHubVaultArchivedDialogFinished()
	}

	public override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		builder //
			.setTitle(R.string.dialog_hub_vault_archived_title) //
			.setNeutralButton(getString(R.string.dialog_hub_vault_archived_positive_button)) { _: DialogInterface, _: Int -> callback?.onHubVaultArchivedDialogFinished() }
			.setOnKeyListener { _, keyCode, _ ->
				if (keyCode == KeyEvent.KEYCODE_BACK) {
					dialog?.dismiss()
					callback?.onHubVaultArchivedDialogFinished()
					true
				} else {
					false
				}
			}
		return builder.create()
	}

	public override fun setupView() {
		super.onStart()
		val dialog = dialog as AlertDialog?
		dialog?.setCanceledOnTouchOutside(false)
	}

	companion object {

		fun newInstance(): HubVaultArchivedDialog {
			return HubVaultArchivedDialog()
		}
	}
}
