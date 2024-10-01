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
import org.cryptomator.presentation.databinding.DialogEnterPasswordBinding
import org.cryptomator.presentation.databinding.ViewDialogErrorBinding
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.model.ProgressStateModel
import org.cryptomator.presentation.model.VaultModel


@Dialog(secure = true)
class EnterPasswordDialog : BaseProgressErrorDialog<EnterPasswordDialog.Callback, DialogEnterPasswordBinding>(DialogEnterPasswordBinding::inflate) {

	// positive button
	private var unlockButton: Button? = null

	interface Callback {

		fun onUnlockClick(vaultModel: VaultModel, password: String)
		fun closeDialog()
		fun onUnlockCanceled()
	}

	override fun onStart() {
		super.onStart()
		val dialog = dialog as AlertDialog?
		dialog?.let {
			unlockButton = it.getButton(android.app.Dialog.BUTTON_POSITIVE)
			unlockButton?.isEnabled = false
			unlockButton?.setOnClickListener {
				showProgress(ProgressModel(ProgressStateModel.UNLOCKING_VAULT))
				val vaultModel = vaultModel()
				callback?.onUnlockClick(vaultModel, binding.etPassword.text.toString())
				onWaitForResponse(binding.etPassword)
				dialog.getButton(android.app.Dialog.BUTTON_NEGATIVE)?.isEnabled = true
			}
			unlockButton?.let { button ->
				binding.etPassword.nextFocusForwardId = button.id
			}
			it.setCanceledOnTouchOutside(false)
			binding.etPassword.requestFocus()
		}
	}

	public override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		return builder //
			.setTitle(vaultModel().name) //
			.setPositiveButton(getString(R.string.dialog_enter_password_positive_button)) { _: DialogInterface, _: Int -> }
			.setNegativeButton(getString(R.string.dialog_button_cancel)) { _: DialogInterface, _: Int ->
				callback?.onUnlockCanceled()
				callback?.closeDialog()
			}.create()
	}

	fun vaultModel(): VaultModel {
		return requireArguments().getSerializable(VAULT_ARG) as VaultModel
	}

	public override fun setupView() {
		unlockButton?.let { registerOnEditorDoneActionAndPerformButtonClick(binding.etPassword) { it } }
		binding.etPassword.addTextChangedListener(object : TextWatcher {
			override fun afterTextChanged(s: Editable) {
				unlockButton?.let { it.isEnabled = s.toString().isNotEmpty() }
			}

			override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
			override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
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
		return binding.etPassword
	}

	companion object {

		private const val VAULT_ARG = "vault"
		fun newInstance(vaultModel: VaultModel): EnterPasswordDialog {
			val dialog = EnterPasswordDialog()
			val args = Bundle()
			args.putSerializable(VAULT_ARG, vaultModel)
			dialog.arguments = args
			return dialog
		}
	}
}
