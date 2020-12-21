package org.cryptomator.presentation.ui.bottomsheet

import android.os.Bundle
import android.view.View
import kotlinx.android.synthetic.main.dialog_bottom_sheet_file_settings.*
import org.cryptomator.generator.BottomSheet
import org.cryptomator.presentation.R
import org.cryptomator.presentation.model.CloudFileModel
import org.cryptomator.presentation.model.CloudNodeModel
import java.util.*

@BottomSheet(R.layout.dialog_bottom_sheet_file_settings)
class FileSettingsBottomSheet : BaseBottomSheet<FileSettingsBottomSheet.Callback>() {

	interface Callback {
		fun onExportFileClicked(cloudFile: CloudFileModel)
		fun onRenameFileClicked(cloudFile: CloudFileModel)
		fun onDeleteNodeClicked(cloudFile: CloudNodeModel<*>)
		fun onShareFileClicked(cloudFile: CloudFileModel)
		fun onMoveFileClicked(cloudFile: CloudFileModel)
		fun onOpenWithTextFileClicked(cloudFile: CloudFileModel)
	}

	override fun setupView() {
		val cloudFileModel = requireArguments().getSerializable(FILE_ARG) as CloudFileModel
		val parentFolderPath = requireArguments().getString(PARENT_FOLDER_PATH_ARG)

		iv_file_image.setImageResource(cloudFileModel.icon.iconResource)
		tv_file_name.text = cloudFileModel.name
		tv_file_path.text = parentFolderPath

		val lowerFileName = cloudFileModel.name.toLowerCase(Locale.getDefault())
		if (lowerFileName.endsWith(".txt") || lowerFileName.endsWith(".md") || lowerFileName.endsWith(".todo")) {
			open_with_text.visibility = View.VISIBLE
			open_with_text.setOnClickListener {
				callback?.onOpenWithTextFileClicked(cloudFileModel)
				dismiss()
			}
		}

		share_file.setOnClickListener {
			callback?.onShareFileClicked(cloudFileModel)
			dismiss()
		}
		move_file.setOnClickListener {
			callback?.onMoveFileClicked(cloudFileModel)
			dismiss()
		}
		export_file.setOnClickListener {
			callback?.onExportFileClicked(cloudFileModel)
			dismiss()
		}
		rename_file.setOnClickListener {
			callback?.onRenameFileClicked(cloudFileModel)
			dismiss()
		}
		delete_file.setOnClickListener {
			callback?.onDeleteNodeClicked(cloudFileModel)
			dismiss()
		}
	}

	companion object {
		private const val FILE_ARG = "file"
		private const val PARENT_FOLDER_PATH_ARG = "parentFolderPath"
		fun newInstance(cloudFileModel: CloudFileModel, parentFolderPath: String): FileSettingsBottomSheet {
			val dialog = FileSettingsBottomSheet()
			val args = Bundle()
			args.putSerializable(FILE_ARG, cloudFileModel)
			args.putString(PARENT_FOLDER_PATH_ARG, parentFolderPath)
			dialog.arguments = args
			return dialog
		}
	}
}
