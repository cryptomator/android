package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import org.cryptomator.domain.Vault
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.DialogCbcPasswordVaultsMigrationBinding

@Dialog
class CBCPasswordVaultsMigrationDialog : BaseDialog<CBCPasswordVaultsMigrationDialog.Callback, DialogCbcPasswordVaultsMigrationBinding>(DialogCbcPasswordVaultsMigrationBinding::inflate) {

	interface Callback {

		fun onCBCPasswordVaultsMigrationClicked(cbcVaults: List<Vault>)
		fun onCBCPasswordVaultsMigrationRejected(cbcVaults: List<Vault>)

	}

	override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		val cbcVaults = requireArguments().getSerializable(VAULTS_ARG) as ArrayList<Vault>
		builder //
			.setTitle(R.string.dialog_cbc_password_vaults_migration_title) //
			.setPositiveButton(getString(R.string.dialog_cbc_password_vaults_migration_action)) { _: DialogInterface, _: Int -> callback?.onCBCPasswordVaultsMigrationClicked(cbcVaults) } //
			.setNegativeButton(getString(R.string.dialog_cbc_password_vaults_migration_cancel)) { _: DialogInterface, _: Int -> callback?.onCBCPasswordVaultsMigrationRejected(cbcVaults) }
		return builder.create()
	}

	public override fun setupView() {
		// empty
	}

	companion object {

		private const val VAULTS_ARG = "vaults"
		fun newInstance(cbcVaults: List<Vault>): DialogFragment {
			return CBCPasswordVaultsMigrationDialog().apply {
				arguments = Bundle().apply {
					putSerializable(VAULTS_ARG, ArrayList(cbcVaults))
				}
			}
		}
	}
}
