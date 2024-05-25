package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.DialogRenameBinding
import org.cryptomator.presentation.databinding.ViewDialogErrorBinding
import org.cryptomator.presentation.model.CloudFileModel
import org.cryptomator.presentation.model.CloudNodeModel
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.model.ProgressStateModel

@Dialog
class CloudNodeRenameDialog : BaseProgressErrorDialog<CloudNodeRenameDialog.Callback, DialogRenameBinding>(DialogRenameBinding::inflate) {

	private var renameConfirmButton: Button? = null

	interface Callback {

		fun onRenameCloudNodeClicked(cloudNodeModel: CloudNodeModel<*>, newCloudNodeName: String)
	}

	override fun onStart() {
		super.onStart()
		val dialog = dialog as AlertDialog?
		dialog?.let {
			renameConfirmButton = dialog.getButton(android.app.Dialog.BUTTON_POSITIVE)
			renameConfirmButton?.isEnabled = false
			renameConfirmButton?.setOnClickListener {
				showProgress(ProgressModel(ProgressStateModel.RENAMING))

				// do action
				val cloudNodeModel = requireArguments().getSerializable(CLOUD_NODE_ARG) as CloudNodeModel<*>
				callback?.onRenameCloudNodeClicked(cloudNodeModel, binding.etRename.text.toString())
				onWaitForResponse(binding.etRename)
			}
			dialog.setCanceledOnTouchOutside(false)
			binding.etRename.requestFocus()
			renameConfirmButton?.let { button ->
				binding.etRename.nextFocusForwardId = button.id
			}
		}
	}

	public override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		val cloudNodeModel = requireArguments().getSerializable(CLOUD_NODE_ARG) as CloudNodeModel<*>
		return builder
			.setTitle(getTitle(cloudNodeModel))
			.setPositiveButton(requireActivity().getString(R.string.dialog_rename_node_positive_button)) { _: DialogInterface, _: Int -> }
			.setNegativeButton(requireActivity().getString(R.string.dialog_button_cancel)) { _: DialogInterface, _: Int -> }
			.create()
	}

	private fun getTitle(cloudNodeModel: CloudNodeModel<*>): String {
		return if (cloudNodeModel.isFolder) {
			getString(R.string.dialog_rename_node_folder_title)
		} else {
			getString(R.string.dialog_rename_node_file_title)
		}
	}

	public override fun setupView() {
		val cloudNodeModel = requireArguments().getSerializable(CLOUD_NODE_ARG) as CloudNodeModel<*>
		binding.etRename.setText(cloudNodeModel.name)
		renameConfirmButton?.let { registerOnEditorDoneActionAndPerformButtonClick(binding.etRename) { it } }
		binding.etRename.addTextChangedListener(object : TextWatcher {
			override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
			override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
			override fun afterTextChanged(s: Editable) {
				renameConfirmButton?.let {
					validateInput(s.toString())
					it.isEnabled = s.toString().isNotEmpty() && !hasInvalidInput(s.toString())
				}
			}
		})
		binding.etRename.onFocusChangeListener = View.OnFocusChangeListener { _: View, _: Boolean ->
			if (cloudNodeModel is CloudFileModel) {
				val indexOf = binding.etRename.text.toString().lastIndexOf(".")
				if (indexOf == -1) {
					binding.etRename.selectAll()
				} else {
					binding.etRename.setSelection(0, indexOf)
				}
			} else {
				binding.etRename.selectAll()
			}
		}
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
		return binding.etRename
	}

	companion object {

		private const val CLOUD_NODE_ARG = "cloudNode"
		fun newInstance(cloudNodeModel: CloudNodeModel<*>): CloudNodeRenameDialog {
			val dialog = CloudNodeRenameDialog()
			val args = Bundle()
			args.putSerializable(CLOUD_NODE_ARG, cloudNodeModel)
			dialog.arguments = args
			return dialog
		}
	}
}
