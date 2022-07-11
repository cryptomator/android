package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import org.cryptomator.domain.UnverifiedVaultConfig
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.model.VaultModel
import org.cryptomator.presentation.util.PasswordStrengthUtil
import kotlinx.android.synthetic.main.dialog_change_password.et_new_password
import kotlinx.android.synthetic.main.dialog_change_password.et_new_retype_password
import kotlinx.android.synthetic.main.dialog_change_password.et_old_password
import kotlinx.android.synthetic.main.view_password_strength_indicator.progressBarPwStrengthIndicator
import kotlinx.android.synthetic.main.view_password_strength_indicator.textViewPwStrengthIndicator

@Dialog(R.layout.dialog_change_password)
class ChangePasswordDialog : BaseProgressErrorDialog<ChangePasswordDialog.Callback>() {

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
						et_old_password.text.toString(),  //
						et_new_password.text.toString(),  //
						et_new_retype_password.text.toString()
					)
				) {
					callback?.onChangePasswordClick(
						vaultModel,  //
						unverifiedVaultConfig, //
						et_old_password.text.toString(),  //
						et_new_password.text.toString()
					)
					onWaitForResponse(et_old_password)
				} else {
					hideKeyboard(et_old_password)
				}
			}
			dialog.setCanceledOnTouchOutside(false)
			et_old_password.requestFocus()
			et_old_password.nextFocusForwardId = et_new_password.id
			et_new_password.nextFocusForwardId = et_new_retype_password.id
			changePasswordButton?.let {
				et_new_retype_password.nextFocusForwardId = it.id
				registerOnEditorDoneActionAndPerformButtonClick(et_new_retype_password) { it }
			}

			PasswordStrengthUtil() //
				.startUpdatingPasswordStrengthMeter(
					et_new_password,  //
					progressBarPwStrengthIndicator,  //
					textViewPwStrengthIndicator, //
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
		et_old_password.requestFocus()
		dialog?.let { showKeyboard(it) }
	}

	override fun enableViewAfterError(): View {
		return et_old_password
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
