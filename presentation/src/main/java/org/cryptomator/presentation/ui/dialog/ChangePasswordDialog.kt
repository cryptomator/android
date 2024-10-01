package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.cryptomator.domain.UnverifiedVaultConfig
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.DialogChangePasswordBinding
import org.cryptomator.presentation.databinding.ViewDialogErrorBinding
import org.cryptomator.presentation.model.VaultModel
import org.cryptomator.presentation.util.PasswordStrengthUtil

@Dialog(secure = true)
class ChangePasswordDialog : BaseProgressErrorDialog<ChangePasswordDialog.Callback, DialogChangePasswordBinding>(DialogChangePasswordBinding::inflate) {

	// positive button
	private var changePasswordButton: Button? = null

	interface Callback {

		fun onChangePasswordClick(vaultModel: VaultModel, unverifiedVaultConfig: UnverifiedVaultConfig?, oldPassword: String, newPassword: String)
		fun onChangePasswordCanceled()
	}

	override fun onStart() {
		super.onStart()
		val dialog = dialog as AlertDialog?
		if (dialog != null) {
			changePasswordButton = dialog.getButton(android.app.Dialog.BUTTON_POSITIVE)
			changePasswordButton?.setOnClickListener {
				val vaultModel = requireArguments().getSerializable(VAULT_ARG) as VaultModel
				val unverifiedVaultConfig = requireArguments().getSerializable(VAULT_CONFIG_ARG) as UnverifiedVaultConfig?
				if (valid(
						binding.etOldPassword.text.toString(),  //
						binding.etNewPassword.text.toString(),  //
						binding.etNewRetypePassword.text.toString()
					)
				) {
					callback?.onChangePasswordClick(
						vaultModel,  //
						unverifiedVaultConfig, //
						binding.etOldPassword.text.toString(),  //
						binding.etNewPassword.text.toString()
					)
					onWaitForResponse(binding.etOldPassword)
				} else {
					hideKeyboard(binding.etOldPassword)
				}
			}
			dialog.setCanceledOnTouchOutside(false)
			binding.etOldPassword.requestFocus()
			binding.etOldPassword.nextFocusForwardId = binding.etNewPassword.id
			binding.etNewPassword.nextFocusForwardId = binding.etNewRetypePassword.id
			changePasswordButton?.let {
				binding.etNewRetypePassword.nextFocusForwardId = it.id
				registerOnEditorDoneActionAndPerformButtonClick(binding.etNewRetypePassword) { it }
			}

			PasswordStrengthUtil() //
				.startUpdatingPasswordStrengthMeter(
					binding.etNewPassword,  //
					binding.viewPasswordStrengthIndicator.pbPasswordStrengthIndicator,  //
					binding.viewPasswordStrengthIndicator.tvPwStrengthIndicator, //
					changePasswordButton
				)
		}
	}

	private fun valid(oldPassword: String, newPassword: String, newRetypedPassword: String): Boolean {
		return when {
			oldPassword.isEmpty() -> {
				showError(R.string.dialog_change_password_msg_old_password_empty)
				false
			}
			newPassword.isEmpty() -> {
				showError(R.string.dialog_change_password_msg_new_password_empty)
				false
			}
			newPassword != newRetypedPassword -> {
				showError(R.string.dialog_change_password_msg_password_mismatch)
				false
			}
			else -> true
		}
	}

	override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		val vaultModel = requireArguments().getSerializable(VAULT_ARG) as VaultModel
		return builder //
			.setTitle(vaultModel.name) //
			.setPositiveButton(getString(R.string.dialog_change_password)) { _: DialogInterface, _: Int -> } //
			.setNegativeButton(getString(R.string.dialog_button_cancel)) { _: DialogInterface?, _: Int -> callback?.onChangePasswordCanceled() } //
			.create()
	}

	override fun setupView() {
		binding.etOldPassword.requestFocus()
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
		return binding.etOldPassword
	}

	companion object {

		private const val VAULT_ARG = "vault"
		private const val VAULT_CONFIG_ARG = "vaultConfig"
		fun newInstance(vaultModel: VaultModel, unverifiedVaultConfig: UnverifiedVaultConfig?): ChangePasswordDialog {
			val args = Bundle()
			args.putSerializable(VAULT_ARG, vaultModel)
			args.putSerializable(VAULT_CONFIG_ARG, unverifiedVaultConfig)
			val fragment = ChangePasswordDialog()
			fragment.arguments = args
			return fragment
		}
	}
}
