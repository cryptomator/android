package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.model.ProgressStateModel
import org.cryptomator.presentation.model.VaultModel
import kotlinx.android.synthetic.main.dialog_rename.et_rename

@Dialog(R.layout.dialog_rename)
class VaultRenameDialog : BaseProgressErrorDialog<VaultRenameDialog.Callback>() {

	private var renameConfirmButton: Button? = null

	interface Callback {

		fun onRenameClick(vaultModel: VaultModel, newVaultName: String)
	}

	override fun onStart() {
		super.onStart()
		val dialog = dialog as AlertDialog?
		dialog?.let {
			renameConfirmButton = dialog.getButton(android.app.Dialog.BUTTON_POSITIVE)
			renameConfirmButton?.isEnabled = false
			renameConfirmButton?.setOnClickListener {
				showProgress(ProgressModel(ProgressStateModel.RENAMING))
				callback?.onRenameClick(requireArguments().getSerializable(VAULT_ARG) as VaultModel, et_rename.text.toString())
				onWaitForResponse(et_rename)
			}
			dialog.setCanceledOnTouchOutside(false)
			et_rename.requestFocus()
			renameConfirmButton?.let { button ->
				et_rename.nextFocusForwardId = button.id
			}
		}
	}

	public override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		return builder
			.setTitle(requireContext().getString(R.string.dialog_rename_vault_title))
			.setPositiveButton(requireContext().getString(R.string.dialog_rename_vault_positive_button)) { _: DialogInterface, _: Int -> }
			.setNegativeButton(requireContext().getString(R.string.dialog_button_cancel)) { _: DialogInterface, _: Int -> }
			.create()
	}

	public override fun setupView() {
		val vaultModel = requireArguments().getSerializable(VAULT_ARG) as VaultModel
		renameConfirmButton?.let { registerOnEditorDoneActionAndPerformButtonClick(et_rename) { it } }
		et_rename.setText(vaultModel.name)
		et_rename.addTextChangedListener(object : TextWatcher {
			override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
			override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
			override fun afterTextChanged(s: Editable) {
				renameConfirmButton?.let {
					validateInput(s.toString())
					it.isEnabled = s.toString().isNotEmpty() && !hasInvalidInput(s.toString())
				}
			}
		})
		dialog?.let { showKeyboard(it) }
	}

	override fun enableViewAfterError(): View {
		return et_rename
	}

	companion object {

		private const val VAULT_ARG = "vault"
		fun newInstance(vaultModel: VaultModel): VaultRenameDialog {
			val dialog = VaultRenameDialog()
			val args = Bundle()
			args.putSerializable(VAULT_ARG, vaultModel)
			dialog.arguments = args
			return dialog
		}
	}
}
