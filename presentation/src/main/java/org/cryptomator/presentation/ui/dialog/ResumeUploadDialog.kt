package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.DialogResumeUploadBinding

@Dialog
class ResumeUploadDialog : BaseDialog<ResumeUploadDialog.Callback, DialogResumeUploadBinding>(DialogResumeUploadBinding::inflate) {

	interface Callback {

		fun onResumeUploadConfirmed(vaultId: Long)
		fun onResumeUploadDeclined(vaultId: Long)
	}

	public override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		val vaultId = requireArguments().getLong(ARG_VAULT_ID)
		val completedCount = requireArguments().getInt(ARG_COMPLETED_COUNT)
		val totalCount = requireArguments().getInt(ARG_TOTAL_COUNT)
		builder //
			.setCancelable(false) //
			.setTitle(R.string.dialog_resume_upload_title) //
			.setMessage(getString(R.string.dialog_resume_upload_message, completedCount, totalCount)) //
			.setPositiveButton(R.string.dialog_resume_upload_resume) { _: DialogInterface, _: Int -> callback?.onResumeUploadConfirmed(vaultId) } //
			.setNegativeButton(R.string.dialog_resume_upload_discard) { _: DialogInterface, _: Int -> callback?.onResumeUploadDeclined(vaultId) }
		return builder.create()
	}

	public override fun setupView() {
		// empty
	}

	companion object {

		private const val ARG_VAULT_ID = "vaultId"
		private const val ARG_COMPLETED_COUNT = "completedCount"
		private const val ARG_TOTAL_COUNT = "totalCount"

		fun newInstance(vaultId: Long, completedCount: Int, totalCount: Int): DialogFragment {
			val dialog = ResumeUploadDialog()
			val args = Bundle()
			args.putLong(ARG_VAULT_ID, vaultId)
			args.putInt(ARG_COMPLETED_COUNT, completedCount)
			args.putInt(ARG_TOTAL_COUNT, totalCount)
			dialog.arguments = args
			return dialog
		}
	}
}
