package org.cryptomator.presentation.ui.dialog

import android.content.Context
import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import org.cryptomator.presentation.R

class ResumeUploadDialog private constructor(private val context: Context) {

	private val callback: Callback

	interface Callback {

		fun onResumeUploadConfirmed(vaultId: Long)
		fun onResumeUploadDeclined(vaultId: Long)
	}

	fun show(vaultId: Long, completedCount: Int, totalCount: Int) {
		AlertDialog.Builder(context)
			.setCancelable(false)
			.setTitle(R.string.dialog_resume_upload_title)
			.setMessage(
				String.format(
					context.getString(R.string.dialog_resume_upload_message),
					completedCount,
					totalCount
				)
			)
			.setPositiveButton(R.string.dialog_resume_upload_resume) { _: DialogInterface?, _: Int ->
				callback.onResumeUploadConfirmed(vaultId)
			}
			.setNegativeButton(R.string.dialog_resume_upload_discard) { _: DialogInterface?, _: Int ->
				callback.onResumeUploadDeclined(vaultId)
			}
			.create()
			.show()
	}

	companion object {

		@JvmStatic
		fun withContext(context: Context): ResumeUploadDialog {
			return ResumeUploadDialog(context)
		}
	}

	init {
		callback = context as Callback
	}
}
