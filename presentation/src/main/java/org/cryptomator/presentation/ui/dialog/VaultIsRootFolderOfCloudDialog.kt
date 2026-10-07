package org.cryptomator.presentation.ui.dialog

import android.app.Activity
import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.DialogVaultIsRootFolderOfCloudBinding

@Dialog
class VaultIsRootFolderOfCloudDialog : BaseDialog<Activity, DialogVaultIsRootFolderOfCloudBinding>(DialogVaultIsRootFolderOfCloudBinding::inflate) {

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
