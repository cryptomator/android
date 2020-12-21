package org.cryptomator.presentation.ui.dialog

import android.content.Context
import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.util.ResourceHelper

class ReplaceDialog private constructor(private val context: Context) {

	private val callback: Callback

	interface Callback {
		fun onReplacePositiveClicked()
		fun onReplaceNegativeClicked()
		fun onReplaceCanceled()
	}

	fun show(existingFiles: List<String>, uploadingFilesCount: Int) {
		val existingFilesCount = existingFiles.size
		val alertDialogBuilder = AlertDialog.Builder(context) //
				.setTitle(effectiveReplaceDialogTitle(existingFilesCount)) //
				.setMessage(effectiveReplaceDialogMessage(existingFiles, uploadingFilesCount))
				.setPositiveButton(effectiveReplaceDialogPositiveButton(existingFilesCount, uploadingFilesCount)) { _: DialogInterface, _: Int -> callback.onReplacePositiveClicked() } //
				.setNeutralButton(effectiveReplaceDialogNeutralButton()) { _: DialogInterface, _: Int -> callback.onReplaceCanceled() } //
				.setOnCancelListener { callback.onReplaceCanceled() }
		if (uploadingFilesCount > 1 && existingFilesCount != uploadingFilesCount) {
			alertDialogBuilder.setNegativeButton(effectiveReplaceDialogNegativeButton()) { _: DialogInterface, _: Int -> callback.onReplaceNegativeClicked() }
		}
		alertDialogBuilder.create().show()
	}

	private fun effectiveReplaceDialogPositiveButton(existingFilesCount: Int, uploadingFilesCount: Int): String {
		return when (existingFilesCount) {
			1 -> {
				ResourceHelper.getString(R.string.dialog_replace_positive_button_single_file_exists)
			}
			uploadingFilesCount -> {
				ResourceHelper.getString(R.string.dialog_replace_positive_button_all_files_exist)
			}
			else -> {
				ResourceHelper.getString(R.string.dialog_replace_positive_button_some_files_exist)
			}
		}
	}

	private fun effectiveReplaceDialogNegativeButton(): String {
		return ResourceHelper.getString(R.string.dialog_replace_negative_button_at_least_two_but_not_all_files_exist)
	}

	private fun effectiveReplaceDialogNeutralButton(): String {
		return ResourceHelper.getString(R.string.dialog_button_cancel)
	}

	private fun effectiveReplaceDialogMessage(existingFiles: List<String>, uploadingFilesCount: Int): String {
		return when (existingFiles.size) {
			1 -> {
				String.format(ResourceHelper.getString(R.string.dialog_replace_msg_single_file_exists), existingFiles[0])
			}
			uploadingFilesCount -> {
				ResourceHelper.getString(R.string.dialog_replace_msg_all_files_exists)
			}
			else -> {
				String.format(ResourceHelper.getString(R.string.dialog_replace_msg_some_files_exists), existingFiles.size)
			}
		}
	}

	companion object {
		fun withContext(context: Context): ReplaceDialog {
			return ReplaceDialog(context)
		}

		private fun effectiveReplaceDialogTitle(existingFilesCount: Int): String {
			return if (existingFilesCount == 1) {
				ResourceHelper.getString(R.string.dialog_replace_title_single_file_exists)
			} else {
				ResourceHelper.getString(R.string.dialog_replace_title_multiple_files_exist)
			}
		}
	}

	init {
		callback = context as Callback
	}
}
