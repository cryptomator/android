package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.DialogCancelUploadAndLockBinding

@Dialog
class CancelUploadAndLockDialog : BaseDialog<CancelUploadAndLockDialog.Callback, DialogCancelUploadAndLockBinding>(DialogCancelUploadAndLockBinding::inflate) {

	interface Callback {

		fun onCancelUploadAndLockConfirmed(vaultId: Long)
	}

	public override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		val vaultId = requireArguments().getLong(ARG_VAULT_ID)
		builder //
			.setCancelable(true) //
			.setTitle(R.string.dialog_cancel_upload_lock_title) //
			.setMessage(R.string.dialog_cancel_upload_lock_message) //
			.setPositiveButton(R.string.dialog_cancel_upload_lock_positive_button) { _: DialogInterface, _: Int -> callback?.onCancelUploadAndLockConfirmed(vaultId) } //
			.setNegativeButton(R.string.dialog_cancel_upload_lock_negative_button) { _: DialogInterface, _: Int -> dismiss() }
		return builder.create()
	}

	public override fun setupView() {
		// empty
	}

	companion object {

		private const val ARG_VAULT_ID = "vaultId"

		fun newInstance(vaultId: Long): DialogFragment {
			val dialog = CancelUploadAndLockDialog()
			val args = Bundle()
			args.putLong(ARG_VAULT_ID, vaultId)
			dialog.arguments = args
			return dialog
		}
	}
}
