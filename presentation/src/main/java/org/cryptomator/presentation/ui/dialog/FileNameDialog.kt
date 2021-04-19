package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.util.FileUtil
import kotlinx.android.synthetic.main.dialog_file_name.file_name

@Dialog(R.layout.dialog_file_name)
class FileNameDialog : BaseProgressErrorDialog<FileNameDialog.Callback>() {

	private var createFileButton: Button? = null

	interface Callback {

		fun onCreateNewTextFileClicked(fileName: String)
	}

	override fun onStart() {
		super.onStart()
		val dialog = dialog as AlertDialog?
		dialog?.let {
			createFileButton = dialog.getButton(android.app.Dialog.BUTTON_POSITIVE)
			createFileButton?.setOnClickListener {
				callback?.onCreateNewTextFileClicked(effectiveTextFileName())
				onWaitForResponse(file_name)
			}
			dialog.setCanceledOnTouchOutside(false)
			file_name.requestFocus()
			createFileButton?.let { button ->
				file_name.nextFocusForwardId = button.id
			}
		}
	}

	private fun effectiveTextFileName(): String {
		return if (file_name.text.toString().isEmpty()) //
			requireContext().getString(R.string.dialog_file_name_placeholder) else  //
			effectiveNewFileName(file_name.text.toString())
	}

	override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		return builder.setTitle(R.string.dialog_file_name_title) //
				.setPositiveButton(R.string.dialog_file_name_create) { _: DialogInterface, _: Int -> } //
				.setNegativeButton(R.string.dialog_file_name_cancel) { _: DialogInterface, _: Int -> } //
				.create()
	}

	private fun effectiveNewFileName(newFileName: String): String {
		val extension = FileUtil.getExtension(newFileName)
		if (extension == null || extension.isEmpty()) {
			return "$newFileName.txt"
		}
		return newFileName
	}

	override fun setupView() {
		registerOnEditorDoneActionAndPerformButtonClick(file_name) { createFileButton }
		file_name.addTextChangedListener(object : TextWatcher {
			override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
			override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
			override fun afterTextChanged(s: Editable) {
				createFileButton?.let {
					validateInput(effectiveNewFileName(s.toString()))
					it.isEnabled = !hasInvalidInput(effectiveNewFileName(s.toString()))
				}
			}
		})
		dialog?.let { showKeyboard(it) }
	}

	override fun enableViewAfterError(): View {
		return file_name
	}
}
