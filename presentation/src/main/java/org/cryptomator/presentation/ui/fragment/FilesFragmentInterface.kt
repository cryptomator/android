package org.cryptomator.presentation.ui.fragment

import org.cryptomator.domain.CloudNode
import org.cryptomator.presentation.intent.ChooseCloudNodeSettings
import org.cryptomator.presentation.model.CloudNodeModel
import org.cryptomator.presentation.model.CloudFolderModel
import org.cryptomator.presentation.model.ProgressModel
import android.view.View
interface FilesFragmentInterface {

	abstract val selectedCloudNodes: List<CloudNodeModel<*>>
	abstract var folder: CloudFolderModel

	abstract fun rootView(): View
	fun selectAllItems()
	abstract fun setSort(comparator: Comparator<CloudNodeModel<*>>)
	abstract fun renderedCloudNodes(): List<CloudNodeModel<*>>
	abstract fun navigationModeChanged(navigationMode: ChooseCloudNodeSettings.NavigationMode)
	abstract fun show(nodes: List<CloudNodeModel<*>>?)
	abstract fun addOrUpdate(cloudNode: CloudNodeModel<*>)
	abstract fun remove(cloudNode: List<CloudNodeModel<*>>?)
	abstract fun replaceRenamedCloudFile(cloudFile: CloudNodeModel<out CloudNode>)
	abstract fun showProgress(node: CloudNodeModel<*>?, progress: ProgressModel?)
	abstract fun showProgress(nodes: List<CloudNodeModel<*>>?, progress: ProgressModel?)
	abstract fun hideProgress(cloudNode : CloudNodeModel<*>?)
	abstract fun hideProgress(nodes : List<CloudNodeModel<*>>?)
	abstract fun showLoading(loading: Boolean?)
	abstract fun setFilterText(query: String)
}