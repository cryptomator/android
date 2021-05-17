package org.cryptomator.presentation.ui.dialog

import android.content.Context
import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.util.ResourceHelper

class NotEnoughVaultsDialog private constructor(private val context: Context) {

	private val callback: Callback
	private var title: String

	interface Callback {

		fun onNotEnoughVaultsOkClicked()
		fun onNotEnoughVaultsCreateVaultClicked()
	}

	fun andTitle(title: String): NotEnoughVaultsDialog {
		this.title = title
		return this
	}

	fun show() {
		AlertDialog.Builder(context) //
			.setCancelable(false) //
			.setTitle(title) //
			.setMessage(ResourceHelper.getString(R.string.dialog_unable_to_share_message)) //
			.setPositiveButton(ResourceHelper.getString(R.string.dialog_unable_to_share_positive_button)) { _: DialogInterface, _: Int -> callback.onNotEnoughVaultsOkClicked() } //
			.setNegativeButton(ResourceHelper.getString(R.string.dialog_unable_to_share_negative_button)) { _: DialogInterface, _: Int -> callback.onNotEnoughVaultsCreateVaultClicked() } //
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
		title = ResourceHelper.getString(R.string.dialog_unable_to_share_title)
	}
}
