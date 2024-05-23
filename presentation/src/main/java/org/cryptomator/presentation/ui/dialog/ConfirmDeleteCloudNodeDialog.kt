package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.DialogConfirmDeleteCloudNodeBinding
import org.cryptomator.presentation.model.CloudFileModel
import org.cryptomator.presentation.model.CloudFolderModel
import org.cryptomator.presentation.model.CloudNodeModel
import java.io.Serializable

@Dialog
class ConfirmDeleteCloudNodeDialog : BaseDialog<ConfirmDeleteCloudNodeDialog.Callback, DialogConfirmDeleteCloudNodeBinding>(DialogConfirmDeleteCloudNodeBinding::inflate) {

	interface Callback {

		fun onDeleteCloudNodeConfirmed(nodes: List<CloudNodeModel<*>>)
	}

	public override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		val nodes = requireArguments().getSerializable(ARG_CLOUD_NODE) as List<CloudNodeModel<*>>
		var title: String? = getString(R.string.dialog_confirm_delete_multiple_title, nodes.size)
		if (nodes.size == 1) {
			title = nodes[0].name
		}
		return builder //
			.setTitle(title) //
			.setPositiveButton(getString(R.string.dialog_confirm_delete_positive_button)) { _: DialogInterface, _: Int -> callback?.onDeleteCloudNodeConfirmed(nodes) } //
			.setNegativeButton(getString(R.string.dialog_confirm_delete_negative_button)) { _: DialogInterface, _: Int -> } //
			.create()
	}

	private fun getMessage(cloudNodeModel: CloudNodeModel<*>): String {
		if (cloudNodeModel is CloudFolderModel) {
			return getString(R.string.dialog_confirm_delete_folder_message)
		} else if (cloudNodeModel is CloudFileModel) {
			return getString(R.string.dialog_confirm_delete_file_message)
		}
		throw IllegalStateException()
	}

	public override fun setupView() {
		val nodes = requireArguments().getSerializable(ARG_CLOUD_NODE) as List<CloudNodeModel<*>>
		binding.tvMessage.text = getString(R.string.dialog_confirm_delete_multiple_message)
		if (nodes.size == 1) {
			binding.tvMessage.text = getMessage(nodes[0])
		}
	}

	companion object {

		private const val ARG_CLOUD_NODE = "cloudNode"
		fun newInstance(nodes: List<CloudNodeModel<*>>): DialogFragment {
			val confirmDeleteCloudNodeDialog = ConfirmDeleteCloudNodeDialog()
			val args = Bundle()
			args.putSerializable(ARG_CLOUD_NODE, nodes as Serializable)
			confirmDeleteCloudNodeDialog.arguments = args
			return confirmDeleteCloudNodeDialog
		}
	}
}
