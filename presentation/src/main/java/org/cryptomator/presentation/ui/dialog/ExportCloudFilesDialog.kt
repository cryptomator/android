package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.DialogUploadLoadingBinding
import org.cryptomator.presentation.databinding.ViewDialogErrorBinding
import org.cryptomator.presentation.model.CloudFileModel
import org.cryptomator.presentation.model.FileProgressStateModel
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.model.ProgressStateModel
import org.cryptomator.presentation.util.ResourceHelper

@Dialog
class ExportCloudFilesDialog : BaseProgressErrorDialog<ExportCloudFilesDialog.Callback, DialogUploadLoadingBinding>(DialogUploadLoadingBinding::inflate) {

	interface Callback {

		fun onExportCancelled()
	}

	private val seenFiles: MutableSet<CloudFileModel> = HashSet()

	override fun onStart() {
		super.onStart()
		showProgress(ProgressModel.GENERIC)
		allowClosingDialog(false)
	}

	override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		return builder //
			.setTitle(effectiveTitle(1)) //
			.setNeutralButton(getString(R.string.dialog_export_file_cancel_button)) { _: DialogInterface, _: Int -> callback?.onExportCancelled() } //
			.create()
	}

	private fun effectiveTitle(seenFiles: Int): String {
		return String.format(
			getString(R.string.dialog_export_file_title),  //
			seenFiles,  //
			requireArguments().getInt(ARG_NUMBER_OF_FILES)
		)
	}

	override fun setupView() {}

	override fun showProgress(progress: ProgressModel) {
		super.showProgress(progress)
		updateSeenFiles(progress)
		if (progress.state() === ProgressStateModel.COMPLETED) {
			dismissAllowingStateLoss()
		} else {
			if (progress.state().imageResourceId() != 0) {
				binding.llDialogIntermediateProgress.ivProgressIcon.setImageDrawable(ResourceHelper.getDrawable(progress.state().imageResourceId()))
			}
			dialog?.setTitle(effectiveTitle(seenFiles.size))
		}
	}

	private fun updateSeenFiles(progressModel: ProgressModel) {
		if (progressModel.state() is FileProgressStateModel) {
			val file = (progressModel.state() as FileProgressStateModel).file
			seenFiles.add(file)
		}
	}

	override fun updateProgress(progress: Int) {
		binding.llDialogIntermediateProgress.pbDialog.progress = progress
	}

	override fun dialogProgressLayout(): LinearLayout {
		return binding.llDialogIntermediateProgress.llProgress
	}

	override fun dialogProgressTextView(): TextView {
		return binding.llDialogIntermediateProgress.tvProgress
	}

	override fun dialogErrorBinding(): ViewDialogErrorBinding {
		return binding.llDialogError
	}

	companion object {

		private const val ARG_NUMBER_OF_FILES = "NUMBER_OF_FILES"
		fun newInstance(numberOfFiles: Int): ExportCloudFilesDialog {
			val args = Bundle()
			args.putInt(ARG_NUMBER_OF_FILES, numberOfFiles)
			val fragment = ExportCloudFilesDialog()
			fragment.arguments = args
			return fragment
		}
	}
}
