package org.cryptomator.presentation.ui.bottomsheet

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import org.cryptomator.generator.BottomSheet
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.DialogBottomSheetFileSettingsBinding
import org.cryptomator.presentation.model.CloudFileModel
import org.cryptomator.presentation.model.CloudNodeModel

@BottomSheet(R.layout.dialog_bottom_sheet_file_settings)
class FileSettingsBottomSheet : BaseBottomSheet<FileSettingsBottomSheet.Callback, DialogBottomSheetFileSettingsBinding>(DialogBottomSheetFileSettingsBinding::inflate) {

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

		binding.ivFileImage.setImageResource(cloudFileModel.icon.iconResource)
		binding.tvFileName.text = cloudFileModel.name
		binding.tvFilePath.text = parentFolderPath
		cloudFileModel.thumbnail?.let {
			val thumbnail = BitmapFactory.decodeFile(it.absolutePath)
			iv_file_image.setImageBitmap(thumbnail)
		}
		if(iv_file_image.drawable == null)
			iv_file_image.setImageResource(cloudFileModel.icon.iconResource)

		tv_file_name.text = cloudFileModel.name
		tv_file_path.text = parentFolderPath

		val lowerFileName = cloudFileModel.name.lowercase()
		if (lowerFileName.endsWith(".txt") || lowerFileName.endsWith(".md") || lowerFileName.endsWith(".todo")) {
			binding.openWithText.visibility = View.VISIBLE
			binding.openWithText.setOnClickListener {
				callback?.onOpenWithTextFileClicked(cloudFileModel)
				dismiss()
			}
		}

		binding.shareFile.setOnClickListener {
			callback?.onShareFileClicked(cloudFileModel)
			dismiss()
		}
		binding.moveFile.setOnClickListener {
			callback?.onMoveFileClicked(cloudFileModel)
			dismiss()
		}
		binding.exportFile.setOnClickListener {
			callback?.onExportFileClicked(cloudFileModel)
			dismiss()
		}
		binding.renameFile.setOnClickListener {
			callback?.onRenameFileClicked(cloudFileModel)
			dismiss()
		}
		binding.deleteFile.setOnClickListener {
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
