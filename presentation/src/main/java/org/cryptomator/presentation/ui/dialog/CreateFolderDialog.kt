package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.model.ProgressStateModel
import kotlinx.android.synthetic.main.dialog_create_folder.et_folder_name

@Dialog(R.layout.dialog_create_folder)
class CreateFolderDialog : BaseProgressErrorDialog<CreateFolderDialog.Callback>() {

	private var createFolderButton: Button? = null

	interface Callback {

		fun onCreateFolderClick(folderName: String)
	}

	override fun onStart() {
		super.onStart()
		val dialog = dialog as AlertDialog?
		dialog?.let {
			createFolderButton = dialog.getButton(android.app.Dialog.BUTTON_POSITIVE)
			createFolderButton?.isEnabled = false
			createFolderButton?.setOnClickListener {
				showProgress(ProgressModel(ProgressStateModel.CREATING_FOLDER))
				// do action
				callback?.onCreateFolderClick(et_folder_name.text.toString())
				onWaitForResponse(et_folder_name)
			}
			dialog.setCanceledOnTouchOutside(false)
			et_folder_name.requestFocus()
			createFolderButton?.let { button ->
				et_folder_name.nextFocusForwardId = button.id
			}
		}
	}

	override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		return builder.setTitle(requireContext().getString(R.string.dialog_create_folder_title))
			.setPositiveButton(requireContext().getString(R.string.dialog_create_folder_positive_button)) { _: DialogInterface, _: Int -> }
			.setNegativeButton(requireContext().getString(R.string.dialog_button_cancel)) { _: DialogInterface, _: Int -> }
			.create()
	}

	override fun setupView() {
		et_folder_name.requestFocus()
		registerOnEditorDoneActionAndPerformButtonClick(et_folder_name) { createFolderButton }
		et_folder_name.addTextChangedListener(object : TextWatcher {
			override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
			override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
			override fun afterTextChanged(s: Editable) {
				if (createFolderButton != null) {
					validateInput(s.toString())
					createFolderButton?.isEnabled = s.toString().isNotEmpty() && !hasInvalidInput(s.toString())
				}
			}
		})
		dialog?.let { showKeyboard(it) }
	}

	override fun enableViewAfterError(): View {
		return et_folder_name
	}
}
