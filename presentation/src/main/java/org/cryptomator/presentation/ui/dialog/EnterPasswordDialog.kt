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
import kotlinx.android.synthetic.main.dialog_enter_password.et_password


@Dialog(R.layout.dialog_enter_password)
class EnterPasswordDialog : BaseProgressErrorDialog<EnterPasswordDialog.Callback>() {

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
				callback?.onUnlockClick(vaultModel, et_password.text.toString())
				onWaitForResponse(et_password)
				dialog.getButton(android.app.Dialog.BUTTON_NEGATIVE)?.isEnabled = true
			}
			unlockButton?.let { button ->
				et_password.nextFocusForwardId = button.id
			}
			it.setCanceledOnTouchOutside(false)
			et_password.requestFocus()
		}
	}

	public override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		return builder //
			.setTitle(vaultModel().name) //
			.setPositiveButton(getString(R.string.dialog_enter_password_positive_button)) { _: DialogInterface, _: Int -> }
			.setNegativeButton(getString(R.string.dialog_enter_password_negative_button)) { _: DialogInterface, _: Int ->
				callback?.onUnlockCanceled()
				callback?.closeDialog()
			}.create()
	}

	fun vaultModel(): VaultModel {
		return requireArguments().getSerializable(VAULT_ARG) as VaultModel
	}

	public override fun setupView() {
		unlockButton?.let { registerOnEditorDoneActionAndPerformButtonClick(et_password) { it } }
		et_password.addTextChangedListener(object : TextWatcher {
			override fun afterTextChanged(s: Editable) {
				unlockButton?.let { it.isEnabled = s.toString().isNotEmpty() }
			}

			override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
			override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
		})
		dialog?.let { showKeyboard(it) }
	}

	override fun enableViewAfterError(): View {
		return et_password
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
