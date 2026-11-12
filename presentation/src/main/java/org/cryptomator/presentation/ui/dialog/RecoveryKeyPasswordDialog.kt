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
import org.cryptomator.domain.UnverifiedVaultConfig
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.DialogRecoveryKeyPasswordBinding
import org.cryptomator.presentation.databinding.ViewDialogErrorBinding
import org.cryptomator.presentation.model.VaultModel

@Dialog(secure = true)
class RecoveryKeyPasswordDialog : BaseProgressErrorDialog<RecoveryKeyPasswordDialog.Callback, DialogRecoveryKeyPasswordBinding>(DialogRecoveryKeyPasswordBinding::inflate) {

	// positive button
	private var showRecoveryKeyButton: Button? = null

	interface Callback {

		fun onShowRecoveryKeyClick(vaultModel: VaultModel, unverifiedVaultConfig: UnverifiedVaultConfig?, password: String)
		fun onShowRecoveryKeyCanceled()
	}

	override fun onStart() {
		super.onStart()
		val dialog = dialog as AlertDialog?
		dialog?.let {
			showRecoveryKeyButton = it.getButton(android.app.Dialog.BUTTON_POSITIVE)
			updateShowRecoveryKeyButton()
			showRecoveryKeyButton?.setOnClickListener {
				val unverifiedVaultConfig = requireArguments().getSerializable(VAULT_CONFIG_ARG) as UnverifiedVaultConfig?
				callback?.onShowRecoveryKeyClick(vaultModel(), unverifiedVaultConfig, binding.etPassword.text.toString())
				onWaitForResponse(binding.etPassword)
			}
			showRecoveryKeyButton?.let { button ->
				binding.etPassword.nextFocusForwardId = button.id
				registerOnEditorDoneActionAndPerformButtonClick(binding.etPassword) { button }
			}
			dialog.setCanceledOnTouchOutside(false)
			binding.etPassword.requestFocus()
		}
	}

	private fun updateShowRecoveryKeyButton() {
		showRecoveryKeyButton?.isEnabled = binding.etPassword.text.toString().isNotEmpty() && !waitingForResponse
	}

	override fun onCancel(dialog: DialogInterface) {
		super.onCancel(dialog)
		callback?.onShowRecoveryKeyCanceled()
	}

	public override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		return builder //
			.setTitle(R.string.dialog_recovery_key_password_title) //
			.setPositiveButton(getString(R.string.dialog_recovery_key_password_positive_button)) { _: DialogInterface, _: Int -> } //
			.setNegativeButton(getString(R.string.dialog_button_cancel)) { _: DialogInterface, _: Int -> callback?.onShowRecoveryKeyCanceled() } //
			.create()
	}

	private fun vaultModel(): VaultModel {
		return requireArguments().getSerializable(VAULT_ARG) as VaultModel
	}

	public override fun setupView() {
		binding.tvHint.text = getString(R.string.dialog_recovery_key_password_hint, vaultModel().name)
		binding.etPassword.addTextChangedListener(object : TextWatcher {
			override fun afterTextChanged(s: Editable) {
				updateShowRecoveryKeyButton()
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
		private const val VAULT_CONFIG_ARG = "vaultConfig"
		fun newInstance(vaultModel: VaultModel, unverifiedVaultConfig: UnverifiedVaultConfig?): RecoveryKeyPasswordDialog {
			val args = Bundle()
			args.putSerializable(VAULT_ARG, vaultModel)
			args.putSerializable(VAULT_CONFIG_ARG, unverifiedVaultConfig)
			val fragment = RecoveryKeyPasswordDialog()
			fragment.arguments = args
			return fragment
		}
	}
}
