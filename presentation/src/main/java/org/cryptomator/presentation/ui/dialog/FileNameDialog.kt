package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.DialogFileNameBinding
import org.cryptomator.presentation.databinding.ViewDialogErrorBinding
import org.cryptomator.presentation.util.FileUtil

@Dialog
class FileNameDialog : BaseProgressErrorDialog<FileNameDialog.Callback, DialogFileNameBinding>(DialogFileNameBinding::inflate) {

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
				onWaitForResponse(binding.etFileName)
			}
			dialog.setCanceledOnTouchOutside(false)
			binding.etFileName.requestFocus()
			createFileButton?.let { button ->
				binding.etFileName.nextFocusForwardId = button.id
			}
		}
	}

	private fun effectiveTextFileName(): String {
		return if (binding.etFileName.text.toString().isEmpty()) //
			requireContext().getString(R.string.dialog_file_name_placeholder) else  //
			effectiveNewFileName(binding.etFileName.text.toString())
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
		createFileButton?.let { registerOnEditorDoneActionAndPerformButtonClick(binding.etFileName) { it } }
		binding.etFileName.addTextChangedListener(object : TextWatcher {
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

	override fun dialogProgressLayout(): LinearLayout {
		return binding.llDialogProgress.llProgress
	}

	override fun dialogProgressTextView(): TextView {
		return binding.llDialogProgress.tvProgress
	}

	override fun dialogErrorBinding(): ViewDialogErrorBinding {
		return binding.llDialogError
	}

	override fun enableViewAfterError(): View {
		return binding.etFileName
	}
}
