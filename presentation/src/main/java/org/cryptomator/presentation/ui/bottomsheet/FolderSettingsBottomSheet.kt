package org.cryptomator.presentation.ui.bottomsheet

import android.os.Build
import android.os.Bundle
import android.view.View
import kotlinx.android.synthetic.main.dialog_bottom_sheet_folder_settings.*
import org.cryptomator.generator.BottomSheet
import org.cryptomator.presentation.R
import org.cryptomator.presentation.model.CloudFolderModel
import org.cryptomator.presentation.model.CloudNodeModel

@BottomSheet(R.layout.dialog_bottom_sheet_folder_settings)
class FolderSettingsBottomSheet : BaseBottomSheet<FolderSettingsBottomSheet.Callback>() {

	interface Callback {
		fun onShareFolderClicked(cloudFolderModel: CloudFolderModel)
		fun onRenameFolderClicked(cloudFolderModel: CloudFolderModel)
		fun onDeleteNodeClicked(cloudFolderModel: CloudNodeModel<*>)
		fun onMoveFolderClicked(cloudFolderModel: CloudFolderModel)
		fun onExportFolderClicked(cloudFolderModel: CloudFolderModel)
	}

	override fun setupView() {
		val cloudFolderModel = requireArguments().getSerializable(FOLDER_ARG) as CloudFolderModel
		val parentFolderPath = requireArguments().getString(PARENT_FOLDER_PATH_ARG)

		tv_folder_name.text = cloudFolderModel.name
		tv_folder_path.text = parentFolderPath

		share_folder.setOnClickListener {
			callback?.onShareFolderClicked(cloudFolderModel)
			dismiss()
		}
		rename_folder.setOnClickListener {
			callback?.onRenameFolderClicked(cloudFolderModel)
			dismiss()
		}
		move_folder.setOnClickListener {
			callback?.onMoveFolderClicked(cloudFolderModel)
			dismiss()
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			export_folder.visibility = View.VISIBLE
			export_folder.setOnClickListener {
				callback?.onExportFolderClicked(cloudFolderModel)
				dismiss()
			}
		}
		delete_folder.setOnClickListener {
			callback?.onDeleteNodeClicked(cloudFolderModel)
			dismiss()
		}
	}

	companion object {
		private const val FOLDER_ARG = "folder"
		private const val PARENT_FOLDER_PATH_ARG = "parentFolderPath"
		fun newInstance(cloudFolderModel: CloudFolderModel, parentFolderPath: String): FolderSettingsBottomSheet {
			val dialog = FolderSettingsBottomSheet()
			val args = Bundle()
			args.putSerializable(FOLDER_ARG, cloudFolderModel)
			args.putString(PARENT_FOLDER_PATH_ARG, parentFolderPath)
			dialog.arguments = args
			return dialog
		}
	}
}
