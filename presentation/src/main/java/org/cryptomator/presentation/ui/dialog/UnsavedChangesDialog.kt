package org.cryptomator.presentation.ui.dialog

import android.content.Context
import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import org.cryptomator.presentation.R

class UnsavedChangesDialog private constructor(private val context: Context) {

	private val callback: Callback

	interface Callback {

		fun onSaveChangesClicked()
		fun onDiscardChangesClicked()
	}

	fun show() {
		AlertDialog.Builder(context) //
			.setCancelable(false) //
			.setTitle(R.string.dialog_unsaved_changes_title) //
			.setMessage(R.string.dialog_unsaved_changes_message) //
			.setPositiveButton(R.string.dialog_unsaved_changes_save) { _: DialogInterface?, _: Int -> callback.onSaveChangesClicked() } //
			.setNegativeButton(R.string.dialog_unsaved_changes_discard) { _: DialogInterface?, _: Int -> callback.onDiscardChangesClicked() } //
			.create().show()
	}

	companion object {

		fun withContext(context: Context): UnsavedChangesDialog {
			return UnsavedChangesDialog(context)
		}
	}

	init {
		callback = context as Callback
	}
}
