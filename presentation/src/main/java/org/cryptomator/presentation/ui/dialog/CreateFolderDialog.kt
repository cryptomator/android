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
import org.cryptomator.presentation.databinding.DialogCreateFolderBinding
import org.cryptomator.presentation.databinding.ViewDialogErrorBinding
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.model.ProgressStateModel

@Dialog
class CreateFolderDialog : BaseProgressErrorDialog<CreateFolderDialog.Callback, DialogCreateFolderBinding>(DialogCreateFolderBinding::inflate) {

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
				callback?.onCreateFolderClick(binding.etFolderName.text.toString())
				onWaitForResponse(binding.etFolderName)
			}
			dialog.setCanceledOnTouchOutside(false)
			binding.etFolderName.requestFocus()
			createFolderButton?.let { button ->
				binding.etFolderName.nextFocusForwardId = button.id
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
		binding.etFolderName.requestFocus()
		createFolderButton?.let {
			registerOnEditorDoneActionAndPerformButtonClick(binding.etFolderName) { it }
		}
		binding.etFolderName.addTextChangedListener(object : TextWatcher {
			override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
			override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
			override fun afterTextChanged(s: Editable) {
				createFolderButton?.let {
					validateInput(s.toString())
					it.isEnabled = s.toString().isNotEmpty() && !hasInvalidInput(s.toString())
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
		return binding.etFolderName
	}
}
