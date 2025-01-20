package org.cryptomator.presentation.ui.dialog

import android.content.Context
import android.content.DialogInterface
import android.view.KeyEvent
import androidx.appcompat.app.AlertDialog
import org.cryptomator.domain.Vault
import org.cryptomator.presentation.R
import org.cryptomator.presentation.util.ResourceHelper

class VaultNotFoundDialog private constructor(private val context: Context) {

	private val callback: Callback

	interface Callback {

		fun onDeleteMissingVaultClicked(vault: Vault)
		fun onCancelMissingVaultClicked(vault: Vault)

	}

	fun show(vault: Vault) {
		AlertDialog.Builder(context) //
			.setTitle(String.format(ResourceHelper.getString(R.string.dialog_vault_not_found_title), vault.name)) //
			.setMessage(ResourceHelper.getString(R.string.dialog_vault_not_found_message)) //
			.setPositiveButton(ResourceHelper.getString(R.string.dialog_vault_not_found_positive_button_text)) { _: DialogInterface, _: Int -> callback.onDeleteMissingVaultClicked(vault) } //
			.setNegativeButton(ResourceHelper.getString(R.string.dialog_button_cancel)) { _: DialogInterface, _: Int -> callback.onCancelMissingVaultClicked(vault) } //
			.setOnCancelListener { callback.onCancelMissingVaultClicked(vault) }
			.setOnKeyListener { _, keyCode, _ ->
				if (keyCode == KeyEvent.KEYCODE_BACK) {
					callback.onCancelMissingVaultClicked(vault)
					true
				} else {
					false
				}
			}
			.create()
			.show()
	}

	companion object {

		@JvmStatic
		fun withContext(context: Context): VaultNotFoundDialog {
			return VaultNotFoundDialog(context)
		}
	}

	init {
		callback = context as Callback
	}
}
