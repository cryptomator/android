package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.DialogRenameBinding
import org.cryptomator.presentation.databinding.ViewDialogErrorBinding
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.model.ProgressStateModel
import org.cryptomator.presentation.model.VaultModel

@Dialog
class VaultRenameDialog : BaseProgressErrorDialog<VaultRenameDialog.Callback, DialogRenameBinding>(DialogRenameBinding::inflate) {

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
				callback?.onRenameClick(requireArguments().getSerializable(VAULT_ARG) as VaultModel, binding.etRename.text.toString())
				onWaitForResponse(binding.etRename)
			}
			dialog.setCanceledOnTouchOutside(false)
			binding.etRename.requestFocus()
			renameConfirmButton?.let { button ->
				binding.etRename.nextFocusForwardId = button.id
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
		renameConfirmButton?.let { registerOnEditorDoneActionAndPerformButtonClick(binding.etRename) { it } }
		binding.etRename.setText(vaultModel.name)
		binding.etRename.addTextChangedListener(object : TextWatcher {
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

	override fun dialogProgressLayout(): LinearLayout {
		return binding.llDialogProgress.llProgress
	}

	override fun dialogProgressTextView(): TextView {
		return binding.llDialogProgress.tvProgress
	}

	override fun dialogErrorBinding(): ViewDialogErrorBinding {
		return binding.llDialogError
	}

	override fun enableViewAfterError(): View {
		return binding.etRename
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
