package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import kotlinx.android.synthetic.main.dialog_existing_file.tv_message

@Dialog(R.layout.dialog_existing_file)
class ExistingFileDialog : BaseDialog<ExistingFileDialog.Callback>() {

	interface Callback {

		fun onReplaceClick(uri: Uri)
	}

	override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		val fileUri = requireArguments().getParcelable<Uri>(FILE_URI_ARG)
		builder.setTitle(getString(R.string.dialog_existing_file_title)) //
			.setPositiveButton(getString(R.string.dialog_existing_file_positive_button)) { _: DialogInterface?, _: Int -> fileUri?.let { callback?.onReplaceClick(it) } } //
			.setNegativeButton(getString(R.string.dialog_button_cancel)) { _: DialogInterface?, _: Int -> }
		return builder.create()
	}

	override fun setupView() {
		val fileName = requireArguments().getString(FILE_NAME_ARG)
		tv_message.text = String.format(getString(R.string.dialog_existing_file_message), fileName)
	}

	companion object {

		private const val FILE_URI_ARG = "fileUri"
		private const val FILE_NAME_ARG = "fileName"
		fun newInstance(uri: Uri, fileName: String): ExistingFileDialog {
			val dialog = ExistingFileDialog()
			val args = Bundle()
			args.putParcelable(FILE_URI_ARG, uri)
			args.putString(FILE_NAME_ARG, fileName)
			dialog.arguments = args
			return dialog
		}
	}
}
