package org.cryptomator.presentation.ui.activity.view

import org.cryptomator.presentation.model.CloudFolderModel
import org.cryptomator.presentation.model.SharedFileModel
import org.cryptomator.presentation.model.VaultModel

interface SharedFilesView : View {

	override fun finish()

	fun displayVaults(vaults: List<VaultModel>)
	fun displayFilesToUpload(sharedFiles: List<SharedFileModel>)
	fun displayDialogUnableToUploadFiles()
	/**
 * Displays a confirmation dialog listing files that already exist at the destination and their combined size.
 *
 * @param existingFiles The names of files that would be replaced if the upload proceeds.
 * @param size The total size, in bytes, of the conflicting files.
 */
fun showReplaceDialog(existingFiles: List<String>, size: Int)
	/**
 * Shows the currently selected destination folder for uploads in the UI.
 *
 * @param folder The cloud folder chosen as the upload destination.
 */
fun showChosenLocation(folder: CloudFolderModel)
	/**
 * Shows an upload progress dialog that reflects the current upload operation.
 *
 * @param uploadingFiles The number of files currently being uploaded to display in the dialog.
 */
fun showUploadDialog(uploadingFiles: Int)
	/**
 * Enables or disables user-initiated upload actions and related controls in the view.
 *
 * @param enabled `true` to allow uploads and enable upload controls, `false` to prevent uploads and disable them.
 */
fun setUploadEnabled(enabled: Boolean)

}
