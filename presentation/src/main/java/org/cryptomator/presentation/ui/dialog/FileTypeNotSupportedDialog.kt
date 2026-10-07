package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.DialogFileTypeNotSupportedBinding
import org.cryptomator.presentation.model.CloudFileModel

@Dialog
class FileTypeNotSupportedDialog : BaseDialog<FileTypeNotSupportedDialog.Callback, DialogFileTypeNotSupportedBinding>(DialogFileTypeNotSupportedBinding::inflate) {

	interface Callback {

		fun onExportFileAfterAppChooserClicked(cloudFile: CloudFileModel)
	}

	override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		val cloudFileModel = requireArguments().getSerializable(CLOUD_FILE_ARG) as CloudFileModel
		builder.setTitle(String.format(getString(R.string.dialog_filetype_not_supported_title), cloudFileModel.name))
			.setPositiveButton(getString(R.string.dialog_filetype_not_supported_positive_button)) { _: DialogInterface, _: Int -> callback?.onExportFileAfterAppChooserClicked(cloudFileModel) } //
			.setNegativeButton(getString(R.string.dialog_button_cancel)) { _: DialogInterface, _: Int -> }
		return builder.create()
	}

	override fun setupView() {}

	companion object {

		private const val CLOUD_FILE_ARG = "cloudFile"
		fun newInstance(cloudFileModel: CloudFileModel): FileTypeNotSupportedDialog {
			val dialog = FileTypeNotSupportedDialog()
			val args = Bundle()
			args.putSerializable(CLOUD_FILE_ARG, cloudFileModel)
			dialog.arguments = args
			return dialog
		}
	}
}
