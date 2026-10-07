package org.cryptomator.presentation.ui.dialog

import android.content.Context
import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import org.cryptomator.presentation.R

class NotEnoughVaultsDialog private constructor(private val context: Context) {

	private val callback: Callback
	private var titleResourceId: Int

	interface Callback {

		fun onNotEnoughVaultsOkClicked()
		fun onNotEnoughVaultsCreateVaultClicked()
	}

	fun andTitle(titleResourceId: Int): NotEnoughVaultsDialog {
		this.titleResourceId = titleResourceId
		return this
	}

	fun show() {
		AlertDialog.Builder(context) //
			.setCancelable(false) //
			.setTitle(titleResourceId) //
			.setMessage(R.string.dialog_unable_to_share_message) //
			.setPositiveButton(R.string.dialog_unable_to_share_positive_button) { _: DialogInterface, _: Int -> callback.onNotEnoughVaultsOkClicked() } //
			.setNegativeButton(R.string.dialog_unable_to_share_negative_button) { _: DialogInterface, _: Int -> callback.onNotEnoughVaultsCreateVaultClicked() } //
			.create() //
			.show()
	}

	companion object {

		fun withContext(context: Context): NotEnoughVaultsDialog {
			return NotEnoughVaultsDialog(context)
		}
	}

	init {
		callback = context as Callback
		titleResourceId = R.string.dialog_unable_to_share_title
	}
}
