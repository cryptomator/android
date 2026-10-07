package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.DialogVaultDeleteConfirmationBinding
import org.cryptomator.presentation.model.VaultModel

@Dialog
class VaultDeleteConfirmationDialog : BaseDialog<VaultDeleteConfirmationDialog.Callback, DialogVaultDeleteConfirmationBinding>(DialogVaultDeleteConfirmationBinding::inflate) {

	interface Callback {

		fun onDeleteConfirmedClick(vaultModel: VaultModel)
	}

	public override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		val vaultModel = requireArguments().getSerializable(VAULT_ARG) as VaultModel
		builder.setTitle(vaultModel.name) //
			.setPositiveButton(getString(R.string.dialog_delete_vault_positive_button_text)) { _: DialogInterface, _: Int -> callback?.onDeleteConfirmedClick(vaultModel) } //
			.setNegativeButton(getString(R.string.dialog_button_cancel)) { _: DialogInterface, _: Int -> }
		return builder.create()
	}

	public override fun setupView() {
		// empty
	}

	companion object {

		private const val VAULT_ARG = "vault"
		fun newInstance(vaultModel: VaultModel): DialogFragment {
			val dialog = VaultDeleteConfirmationDialog()
			val args = Bundle()
			args.putSerializable(VAULT_ARG, vaultModel)
			dialog.arguments = args
			return dialog
		}
	}
}
