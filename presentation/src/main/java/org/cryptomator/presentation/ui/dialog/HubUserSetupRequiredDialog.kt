package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import android.os.Bundle
import android.view.KeyEvent
import androidx.appcompat.app.AlertDialog
import org.cryptomator.domain.UnverifiedHubVaultConfig
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.DialogHubUserSetupRequiredBinding

@Dialog
class HubUserSetupRequiredDialog : BaseDialog<HubUserSetupRequiredDialog.Callback, DialogHubUserSetupRequiredBinding>(DialogHubUserSetupRequiredBinding::inflate) {

	interface Callback {

		fun onGoToHubProfileClicked(unverifiedVaultConfig: UnverifiedHubVaultConfig)
		fun onCancelHubUserSetupClicked()

	}

	public override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		builder //
			.setTitle(R.string.dialog_hub_user_setup_required_title) //
			.setPositiveButton(getString(R.string.dialog_hub_user_setup_required_neutral_button)) { _: DialogInterface, _: Int -> } //
			.setNegativeButton(getString(R.string.dialog_hub_user_setup_required_negative_button)) { _: DialogInterface, _: Int -> callback?.onCancelHubUserSetupClicked() } //
			.setOnKeyListener { _, keyCode, _ ->
				if (keyCode == KeyEvent.KEYCODE_BACK) {
					dialog?.dismiss()
					callback?.onCancelHubUserSetupClicked()
					true
				} else {
					false
				}
			}
		return builder.create()
	}

	override fun onStart() {
		super.onStart()
		val dialog = dialog as AlertDialog?
		val goToProfileButton = dialog?.getButton(android.app.Dialog.BUTTON_POSITIVE)
		goToProfileButton?.setOnClickListener {
			val unverifiedVaultConfig = requireArguments().getSerializable(VAULT_CONFIG_ARG) as UnverifiedHubVaultConfig
			callback?.onGoToHubProfileClicked(unverifiedVaultConfig)
		}
		dialog?.setCanceledOnTouchOutside(false)
	}

	override fun setupView() {
		// empty
	}

	companion object {

		private const val VAULT_CONFIG_ARG = "vaultConfig"
		fun newInstance(unverifiedVaultConfig: UnverifiedHubVaultConfig): HubUserSetupRequiredDialog {
			val args = Bundle()
			args.putSerializable(VAULT_CONFIG_ARG, unverifiedVaultConfig)
			val fragment = HubUserSetupRequiredDialog()
			fragment.arguments = args
			return fragment
		}
	}
}
