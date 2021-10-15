package org.cryptomator.presentation.ui.activity.view

import org.cryptomator.presentation.model.CloudFolderModel
import org.cryptomator.presentation.model.SharedFileModel
import org.cryptomator.presentation.model.VaultModel

interface SharedFilesView : View {

	override fun finish()

	fun displayVaults(vaults: List<VaultModel>)
	fun displayFilesToUpload(sharedFiles: List<SharedFileModel>)
	fun displayDialogUnableToUploadFiles()
	fun showReplaceDialog(existingFiles: List<String>, size: Int)
	fun showChosenLocation(folder: CloudFolderModel)
	fun showUploadDialog(uploadingFiles: Int)

}
