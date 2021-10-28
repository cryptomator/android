package org.cryptomator.presentation.ui.dialog

import android.app.Activity
import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R

@Dialog(R.layout.dialog_vault_is_root_folder_of_cloud)
class VaultIsRootFolderOfCloudDialog : BaseDialog<Activity>() {

	public override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		return builder //
			.setTitle(R.string.dialog_vault_is_root_folder_of_cloud_title) //
			.setNeutralButton(R.string.dialog_vault_is_root_folder_of_cloud_neutral_button) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
			.create()
	}

	override fun setupView() {}

	companion object {

		fun newInstance(): DialogFragment {
			return VaultIsRootFolderOfCloudDialog()
		}
	}
}
