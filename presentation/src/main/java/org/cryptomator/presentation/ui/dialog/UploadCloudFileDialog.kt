package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.model.FileProgressStateModel
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.model.ProgressStateModel
import org.cryptomator.presentation.util.ResourceHelper
import kotlinx.android.synthetic.main.dialog_upload_loading.file_upload
import kotlinx.android.synthetic.main.view_dialog_intermediate_progress.iv_progress_icon
import kotlinx.android.synthetic.main.view_dialog_intermediate_progress.pb_dialog

@Dialog(R.layout.dialog_upload_loading)
class UploadCloudFileDialog : BaseProgressErrorDialog<UploadCloudFileDialog.Callback>() {

	private var numberOfFileCurrentlyUploaded = 0
	private var encryptionProgressMeansTheNextFileIsUploaded = true

	interface Callback {

		fun onUploadCanceled()
	}

	override fun onStart() {
		super.onStart()
		showProgress(ProgressModel.GENERIC)
		allowClosingDialog(false)
		enableOrientationChange(false)
		val dialog = dialog as AlertDialog?
		dialog?.let {
			val cancelButton = dialog.getButton(android.app.Dialog.BUTTON_NEUTRAL)
			cancelButton.setOnClickListener {
				callback?.onUploadCanceled()
				cancelButton.isEnabled = false
			}
		}
	}

	override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		return builder //
				.setTitle(effectiveTitle()) //
				.setNeutralButton(getString(R.string.dialog_upload_file_cancel_button)) { _: DialogInterface?, _: Int -> } //
				.create()
	}

	private fun effectiveTitle(): String {
		return getString(R.string.dialog_upload_file_title)
	}

	override fun setupView() {}

	override fun showProgress(progress: ProgressModel) {
		super.showProgress(progress)
		if (progress.state() === ProgressStateModel.COMPLETED) {
			dismissAllowingStateLoss()
		} else {
			updateRemainingFilesText(progress)
			if (progress.state().imageResourceId() != 0) {
				iv_progress_icon?.setImageDrawable(ResourceHelper.getDrawable(progress.state().imageResourceId()))
			}
		}
	}

	private fun updateRemainingFilesText(progress: ProgressModel) {
		if (progress.state() is FileProgressStateModel) {
			updateRemainingFilesText(progress.state() as FileProgressStateModel)
		}
	}

	private fun updateRemainingFilesText(state: FileProgressStateModel) {
		if (state.`is`(FileProgressStateModel.ENCRYPTION)) {
			if (encryptionProgressMeansTheNextFileIsUploaded) {
				encryptionProgressMeansTheNextFileIsUploaded = false
				numberOfFileCurrentlyUploaded++
				file_upload?.text = String.format(getString(R.string.dialog_upload_file_remaining), numberOfFileCurrentlyUploaded, numberOfUploadedFiles())
			}
		} else {
			encryptionProgressMeansTheNextFileIsUploaded = true
		}
	}

	private fun numberOfUploadedFiles(): Int {
		return requireArguments().getSerializable(ARG_NUMBER_OF_UPLOADED_FILES) as Int
	}

	override fun updateProgress(progress: Int) {
		pb_dialog.progress = progress
	}

	companion object {

		private const val ARG_NUMBER_OF_UPLOADED_FILES = "totalUploadingFiles"
		fun newInstance(numberOfUploadedFiles: Int): UploadCloudFileDialog {
			val args = Bundle()
			args.putSerializable(ARG_NUMBER_OF_UPLOADED_FILES, numberOfUploadedFiles)
			val fragment = UploadCloudFileDialog()
			fragment.arguments = args
			return fragment
		}
	}
}
