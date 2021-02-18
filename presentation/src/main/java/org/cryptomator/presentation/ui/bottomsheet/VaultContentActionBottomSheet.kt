package org.cryptomator.presentation.ui.bottomsheet

import android.os.Bundle
import org.cryptomator.generator.BottomSheet
import org.cryptomator.presentation.R
import org.cryptomator.presentation.model.CloudFolderModel
import kotlinx.android.synthetic.main.dialog_bottom_sheet_vault_action.create_new_folder
import kotlinx.android.synthetic.main.dialog_bottom_sheet_vault_action.create_new_text_file
import kotlinx.android.synthetic.main.dialog_bottom_sheet_vault_action.title
import kotlinx.android.synthetic.main.dialog_bottom_sheet_vault_action.upload_files

@BottomSheet(R.layout.dialog_bottom_sheet_vault_action)
class VaultContentActionBottomSheet : BaseBottomSheet<VaultContentActionBottomSheet.Callback>() {

	interface Callback {

		fun onCreateNewFolderClicked()
		fun onUploadFilesClicked(folder: CloudFolderModel)
		fun onCreateNewTextFileClicked()
	}

	override fun setupView() {
		val folder = requireArguments().getSerializable(FOLDER_ARG) as CloudFolderModel

		title.text = String.format(getString(R.string.screen_file_browser_actions_title), folderPath(folder))

		create_new_folder.setOnClickListener {
			callback?.onCreateNewFolderClicked()
			dismiss()
		}
		upload_files.setOnClickListener {
			callback?.onUploadFilesClicked(folder)
			dismiss()
		}
		create_new_text_file.setOnClickListener {
			callback?.onCreateNewTextFileClicked()
			dismiss()
		}
	}

	private fun folderPath(folder: CloudFolderModel): String {
		val vault = folder.vault()
		return if (vault == null) {
			folder.path
		} else {
			vault.path + folder.path
		}
	}

	companion object {

		private const val FOLDER_ARG = "folder"
		fun newInstance(folder: CloudFolderModel): VaultContentActionBottomSheet {
			val dialog = VaultContentActionBottomSheet()
			val args = Bundle()
			args.putSerializable(FOLDER_ARG, folder)
			dialog.arguments = args
			return dialog
		}
	}
}
