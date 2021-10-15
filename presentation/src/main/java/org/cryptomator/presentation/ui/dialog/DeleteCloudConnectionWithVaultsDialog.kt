package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import org.cryptomator.domain.Vault
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.model.CloudModel
import java.util.ArrayList

@Dialog(R.layout.dialog_delete_cloud_connection_with_vaults)
class DeleteCloudConnectionWithVaultsDialog : BaseDialog<DeleteCloudConnectionWithVaultsDialog.Callback>() {

	interface Callback {

		fun onDeleteCloudConnectionAndVaults(cloudModel: CloudModel, vaultsOfCloud: ArrayList<Vault>)
	}

	override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		val cloudModel = requireArguments().getSerializable(ARG_CLOUD) as CloudModel
		builder.setTitle(cloudModel.name()) //
			.setPositiveButton(getString(R.string.dialog_delete_cloud_connection_with_vaults_positive_button)) { _: DialogInterface, _: Int ->
				val vaultsOfCloud = requireArguments().getSerializable(ARG_VAULTS) as ArrayList<Vault>
				callback?.onDeleteCloudConnectionAndVaults(cloudModel, vaultsOfCloud)
				dismiss()
			}.setNegativeButton(getString(R.string.dialog_delete_cloud_connection_with_vaults_negative_button)) { _: DialogInterface, _: Int -> }
		return builder.create()
	}

	override fun setupView() {}

	companion object {

		private const val ARG_CLOUD = "cloud"
		private const val ARG_VAULTS = "vaults"
		fun newInstance(cloudModel: CloudModel, vaultsOfCloud: ArrayList<Vault>): DeleteCloudConnectionWithVaultsDialog {
			val args = Bundle()
			args.putSerializable(ARG_CLOUD, cloudModel)
			args.putSerializable(ARG_VAULTS, vaultsOfCloud)
			val fragment = DeleteCloudConnectionWithVaultsDialog()
			fragment.arguments = args
			return fragment
		}
	}
}
