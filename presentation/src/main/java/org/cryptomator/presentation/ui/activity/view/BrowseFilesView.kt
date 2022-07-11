package org.cryptomator.presentation.ui.activity.view

import org.cryptomator.domain.CloudNode
import org.cryptomator.presentation.model.CloudFileModel
import org.cryptomator.presentation.model.CloudFolderModel
import org.cryptomator.presentation.model.CloudNodeModel
import org.cryptomator.presentation.model.ProgressModel

interface BrowseFilesView : View {

	val folder: CloudFolderModel

	fun showCloudNodes(nodes: List<CloudNodeModel<*>>)
	fun addOrUpdateCloudNode(node: CloudNodeModel<*>)
	fun deleteCloudNodesFromAdapter(nodes: List<CloudNodeModel<*>>)
	fun replaceRenamedCloudNode(node: CloudNodeModel<out CloudNode>)
	fun showLoading(loading: Boolean)
	fun showProgress(node: CloudNodeModel<*>, progress: ProgressModel)
	fun showProgress(nodes: List<CloudNodeModel<*>>, progress: ProgressModel)
	fun hideProgress(node: CloudNodeModel<*>)
	fun hideProgress(nodes: List<CloudNodeModel<*>>)
	fun showFileTypeNotSupportedDialog(file: CloudFileModel)
	fun showReplaceDialog(existingFiles: List<String>, size: Int)
	fun showUploadDialog(uploadingFiles: Int)
	fun renderedCloudNodes(): List<CloudNodeModel<*>>
	fun hasExcludedFolder(): Boolean
	fun navigateTo(folder: CloudFolderModel)
	fun updateTitle(folder: CloudFolderModel)
	fun showAddContentDialog()
	fun showNodeSettingsDialog(node: CloudNodeModel<*>)
	fun disableGeneralSelectionActions()
	fun enableGeneralSelectionActions()
	fun enableSelectionMode()
	fun updateSelectionTitle(numberSelected: Int)
	fun disableSelectionMode()
	fun showSymLinkDialog()
	fun showNoDirFileOrEmptyDialog(cryptoFolderName: String, cloudFolderPath: String)
	fun updateActiveFolderDueToAuthenticationProblem(folder: CloudFolderModel)

}
