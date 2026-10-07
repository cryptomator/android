package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import org.cryptomator.domain.UnverifiedHubVaultConfig
import org.cryptomator.domain.UnverifiedVaultConfig
import org.cryptomator.domain.Vault
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.DialogHubCheckHostAuthenticityBinding

@Dialog
class HubCheckHostAuthenticityDialog : BaseDialog<HubCheckHostAuthenticityDialog.Callback, DialogHubCheckHostAuthenticityBinding>(DialogHubCheckHostAuthenticityBinding::inflate) {

	interface Callback {

		fun onHubCheckHostsAllowed(unverifiedHubVaultConfig: UnverifiedHubVaultConfig, vault: Vault)
		fun onHubCheckHostsDenied(unverifiedHubVaultConfig: UnverifiedHubVaultConfig)

	}

	public override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		val unverifiedHubVaultConfig = requireArguments().getSerializable(UNVERIFIED_VAULT_CONFIG_ARG) as UnverifiedHubVaultConfig
		val vault = requireArguments().getSerializable(VAULT_ARG) as Vault
		return builder //
			.setTitle(R.string.dialog_hub_check_host_authenticity_title) //
			.setPositiveButton(requireActivity().getString(R.string.dialog_hub_check_host_authenticity_neutral_button)) { _: DialogInterface, _: Int -> callback?.onHubCheckHostsAllowed(unverifiedHubVaultConfig, vault) }
			.setNegativeButton(requireActivity().getString(R.string.dialog_button_cancel)) { _: DialogInterface, _: Int -> callback?.onHubCheckHostsDenied(unverifiedHubVaultConfig) }
			.create()
	}

	override fun onStart() {
		super.onStart()
		val dialog = dialog as AlertDialog?
		dialog?.setCanceledOnTouchOutside(false)
	}

	public override fun setupView() {
		val hostnames = requireArguments().getSerializable(HOSTNAMES_ARG) as Array<String>
		binding.tvHostnames.text =  hostnames.sorted().joinToString(separator = "\n") { "• $it" }
	}

	companion object {
		private const val HOSTNAMES_ARG = "hostnames"
		private const val UNVERIFIED_VAULT_CONFIG_ARG = "unverifiedVaultConfig"
		private const val VAULT_ARG = "vault"
		fun newInstance(hostnames: Array<String>, unverifiedVaultConfig: UnverifiedVaultConfig, vault: Vault): HubCheckHostAuthenticityDialog {
			val dialog = HubCheckHostAuthenticityDialog()
			val args = Bundle()
			args.putSerializable(HOSTNAMES_ARG, hostnames)
			args.putSerializable(UNVERIFIED_VAULT_CONFIG_ARG, unverifiedVaultConfig)
			args.putSerializable(VAULT_ARG, vault)
			dialog.arguments = args
			return dialog
		}
	}
}
