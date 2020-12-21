package org.cryptomator.presentation.ui.adapter

import android.os.PatternMatcher
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView
import kotlinx.android.synthetic.main.item_browse_files_node.view.*
import kotlinx.android.synthetic.main.view_cloud_file_content.view.*
import kotlinx.android.synthetic.main.view_cloud_file_progress.view.*
import kotlinx.android.synthetic.main.view_cloud_folder_content.view.*
import org.cryptomator.domain.CloudNode
import org.cryptomator.presentation.R
import org.cryptomator.presentation.intent.ChooseCloudNodeSettings
import org.cryptomator.presentation.intent.ChooseCloudNodeSettings.NavigationMode.BROWSE_FILES
import org.cryptomator.presentation.intent.ChooseCloudNodeSettings.NavigationMode.SELECT_ITEMS
import org.cryptomator.presentation.model.CloudFileModel
import org.cryptomator.presentation.model.CloudFolderModel
import org.cryptomator.presentation.model.CloudNodeModel
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.model.ProgressStateModel.Companion.COMPLETED
import org.cryptomator.presentation.model.comparator.*
import org.cryptomator.presentation.ui.adapter.BrowseFilesAdapter.VaultContentViewHolder
import org.cryptomator.presentation.util.DateHelper
import org.cryptomator.presentation.util.FileIcon
import org.cryptomator.presentation.util.FileSizeHelper
import org.cryptomator.presentation.util.FileUtil
import org.cryptomator.presentation.util.ResourceHelper.Companion.getDrawable
import org.cryptomator.util.Optional
import org.cryptomator.util.SharedPreferencesHandler
import java.util.*
import javax.inject.Inject

class BrowseFilesAdapter @Inject
constructor(private val dateHelper: DateHelper, //
			private val fileSizeHelper: FileSizeHelper, //
			private val fileUtil: FileUtil, //
			private val sharedPreferencesHandler: SharedPreferencesHandler) : RecyclerViewBaseAdapter<CloudNodeModel<*>, BrowseFilesAdapter.ItemClickListener, VaultContentViewHolder>(CloudNodeModelNameAZComparator()), FastScrollRecyclerView.SectionedAdapter {

	private var chooseCloudNodeSettings: ChooseCloudNodeSettings? = null
	private var navigationMode: ChooseCloudNodeSettings.NavigationMode? = null

	private val isInSelectionMode: Boolean
		get() = chooseCloudNodeSettings != null

	override fun getItemLayout(viewType: Int): Int {
		return R.layout.item_browse_files_node
	}

	override fun createViewHolder(view: View, viewType: Int): VaultContentViewHolder {
		return VaultContentViewHolder(view)
	}

	fun addOrReplaceCloudNode(cloudNodeModel: CloudNodeModel<*>) {
		if (contains(cloudNodeModel)) {
			replaceItem(cloudNodeModel)
		} else {
			addItem(cloudNodeModel)
		}
	}

	fun replaceRenamedCloudFile(cloudNode: CloudNodeModel<out CloudNode>) {
		itemCollection.forEach { nodes ->
			if (nodes.javaClass == cloudNode.javaClass && nodes.name == cloudNode.oldName) {
				val position = positionOf(nodes)
				replaceItem(position, cloudNode)
				return
			}
		}
	}

	override fun setCallback(callback: ItemClickListener) {
		this.callback = callback
	}

	fun setChooseCloudNodeSettings(chooseCloudNodeSettings: ChooseCloudNodeSettings?) {
		this.chooseCloudNodeSettings = chooseCloudNodeSettings
	}

	fun updateNavigationMode(navigationMode: ChooseCloudNodeSettings.NavigationMode) {
		this.navigationMode = navigationMode
		if (isNavigationMode(BROWSE_FILES)) {
			itemCollection.forEach { node ->
				node.isSelected = false
			}
		}
		notifyDataSetChanged()
	}

	fun renderedCloudNodes(): List<CloudNodeModel<*>> {
		return itemCollection
	}

	fun selectedCloudNodes(): List<CloudNodeModel<*>> {
		return all.filter { it.isSelected }
	}

	fun hasUnSelectedNode(): Boolean {
		return itemCount > selectedCloudNodes().size
	}

	fun filterNodes(nodes: List<CloudNodeModel<*>>?, filterText: String): List<CloudNodeModel<*>>? {
		return if (filterText.isNotEmpty()) {
			if (sharedPreferencesHandler.useGlobSearch()) {
				nodes?.filter { cloudNode -> PatternMatcher(filterText, PatternMatcher.PATTERN_SIMPLE_GLOB).match(cloudNode.name) }
			} else {
				nodes?.filter { cloudNode -> cloudNode.name.toLowerCase(Locale.getDefault()).startsWith(filterText.toLowerCase(Locale.getDefault())) }
			}
		} else {
			nodes
		}
	}

	inner class VaultContentViewHolder internal constructor(itemView: View) : RecyclerViewBaseAdapter<*, *, *>.ItemViewHolder(itemView) {

		private var uiState: UiStateTest? = null

		private var currentProgressIcon: Int = 0

		private var bound: CloudNodeModel<*>? = null

		override fun bind(position: Int) {
			bound = getItem(position)
			bound?.let { internalBind(it) }
		}

		private fun internalBind(node: CloudNodeModel<*>) {
			bindNodeImage(node)
			bindSettings(node)
			bindLongNodeClick(node)
			bindFileOrFolder(node)
		}

		private fun bindNodeImage(node: CloudNodeModel<*>) {
			itemView.cloudNodeImage.setImageResource(bindCloudNodeImage(node))
		}

		private fun bindCloudNodeImage(cloudNodeModel: CloudNodeModel<*>): Int {
			if (cloudNodeModel is CloudFileModel) {
				return FileIcon.fileIconFor(cloudNodeModel.name, fileUtil).iconResource
			} else if (cloudNodeModel is CloudFolderModel) {
				return R.drawable.node_folder
			}
			throw IllegalStateException("Could not identify the CloudNodeModel type")
		}

		private fun bindSettings(node: CloudNodeModel<*>) {
			itemView.settings.setOnClickListener { callback.onNodeSettingsClicked(node) }
		}

		private fun bindLongNodeClick(node: CloudNodeModel<*>) {
			enableNodeLongClick {
				node.isSelected = true
				callback.onNodeLongClicked()
				true
			}
		}

		private fun bindFileOrFolder(node: CloudNodeModel<*>) {
			if (node is CloudFileModel) {
				internalBind(node)
			} else {
				internalBind(node as CloudFolderModel)
			}
		}

		private fun internalBind(file: CloudFileModel) {
			switchTo(FileDetails())
			bindFile(file)
			bindProgressIfPresent(file)
			bindSelectItemsModeIfPresent(file)
			bindFileSelectionModeIfPresent(file)
		}

		private fun bindFile(file: CloudFileModel) {
			itemView.cloudFileText.text = file.name
			itemView.cloudFileSubText.text = fileDetails(file)

			enableNodeClick { callback.onFileClicked(file) }
		}

		private fun bindFileSelectionModeIfPresent(file: CloudFileModel) {
			if (isInSelectionMode) {
				disableNodeLongClick()
				hideSettings()
				if (!isSelectable(file)) {
					itemView.cloudFileSubText.visibility = GONE
					itemView.cloudFileSubText.text = ""
					itemView.isEnabled = false
				}
			}
		}

		private fun internalBind(folder: CloudFolderModel) {
			switchTo(FolderDetails())
			bindFolder(folder)
			bindSelectItemsModeIfPresent(folder)
			bindFolderSelectionModeIfPresent(folder)
			bindProgressIfPresent(folder)
		}

		private fun bindSelectItemsModeIfPresent(node: CloudNodeModel<*>) {
			if (isNavigationMode(SELECT_ITEMS)) {
				if (node is CloudFileModel) {
					switchTo(FileSelection())
				} else {
					switchTo(FolderSelection())
				}
				disableNodeLongClick()
				bindNodeSelection(node)
			}
		}

		private fun bindProgressIfPresent(node: CloudNodeModel<*>) {
			val progress = node.progress
			if (progress.isPresent) {
				showProgress(progress.get())
			}
		}

		private fun bindFolder(folder: CloudFolderModel) {
			itemView.cloudFolderText.text = folder.name
			enableNodeClick { callback.onFolderClicked(folder) }
		}

		private fun bindFolderSelectionModeIfPresent(folder: CloudFolderModel) {
			if (isInSelectionMode) {
				disableNodeLongClick()
				hideSettings()
				if (!isSelectable(folder)) {
					itemView.isEnabled = false
				}
			}
		}

		private fun hideSettings() {
			itemView.settings.visibility = GONE
		}

		private fun bindNodeSelection(cloudNodeModel: CloudNodeModel<*>) {
			itemView.itemCheckBox.setOnCheckedChangeListener { _, isChecked ->
				cloudNodeModel.isSelected = isChecked
				callback.onSelectedNodesChanged(selectedCloudNodes().size)
			}
			enableNodeClick { itemView.itemCheckBox.toggle() }

			itemView.itemCheckBox.isChecked = cloudNodeModel.isSelected
		}

		private fun fileDetails(cloudFile: CloudFileModel): String {
			val formattedFileSize = fileSizeHelper.getFormattedFileSize(cloudFile.size)
			val formattedModifiedDate = dateHelper.getFormattedModifiedDate(cloudFile.modified)

			return if (formattedFileSize.isPresent) {
				if (formattedModifiedDate.isPresent) {
					formattedFileSize.get() + " • " + formattedModifiedDate.get()
				} else {
					formattedFileSize.get()
				}
			} else if (formattedModifiedDate.isPresent) {
				formattedModifiedDate.get()
			} else {
				""
			}
		}

		fun showProgress(progress: ProgressModel?) {
			bound?.progress = Optional.of(progress)
			when {
				progress?.state() === COMPLETED -> hideProgress()
				progress?.progress() == ProgressModel.UNKNOWN_PROGRESS_PERCENTAGE -> showIndeterminateProgress(progress)
				progress?.state() !== COMPLETED -> progress?.let { showDeterminateProgress(it) }
			}
		}

		private fun showIndeterminateProgress(progress: ProgressModel) {
			uiState?.let { switchTo(it.indeterminateProgress()) }
			if (uiState?.isForFile == true) {
				itemView.cloudFileSubText.setText(progress.state().textResourceId())
			} else {
				itemView.cloudFolderActionText.setText(progress.state().textResourceId())
			}

			if (!progress.state().isSelectable) {
				disableNodeActions()
			}
		}

		private fun disableNodeActions() {
			itemView.isEnabled = false
			itemView.settings.visibility = GONE
		}

		private fun enableNodeClick(clickListener: View.OnClickListener) {
			itemView.setOnClickListener(clickListener)
		}

		private fun enableNodeLongClick(longClickListener: View.OnLongClickListener) {
			itemView.setOnLongClickListener(longClickListener)
		}

		private fun disableNodeLongClick() {
			itemView.setOnLongClickListener(null)
		}

		private fun showDeterminateProgress(progress: ProgressModel) {
			uiState?.let { switchTo(it.determinateProgress()) }
			if (uiState?.isForFile == true) {
				disableNodeActions()
				itemView.cloudFile.progress = progress.progress()
				if (currentProgressIcon != progress.state().imageResourceId()) {
					currentProgressIcon = progress.state().imageResourceId()
					itemView.progressIcon.setImageDrawable(getDrawable(currentProgressIcon))
				}
			} else {
				// no determinate progress for folders
				itemView.cloudFolderActionText.setText(progress.state().textResourceId())
			}
		}

		fun hideProgress() {
			uiState?.let { switchTo(it.details()) }
			bound?.progress = Optional.empty()
		}

		private fun switchTo(state: UiStateTest) {
			if (uiState !== state) {
				uiState = state
				uiState?.apply()
			}
		}

		fun selectNode(checked: Boolean) {
			itemView.itemCheckBox.isChecked = checked
		}

		abstract inner class UiStateTest(val isForFile: Boolean) {
			fun details(): UiStateTest {
				return if (isForFile) {
					FileDetails()
				} else {
					FolderDetails()
				}
			}

			fun determinateProgress(): UiStateTest {
				return if (isForFile) {
					FileDeterminateProgress()
				} else {
					FolderIndeterminateProgress() // no determinate progress for folders
				}
			}

			fun indeterminateProgress(): UiStateTest {
				return if (isForFile) {
					FileIndeterminateProgress()
				} else {
					FolderIndeterminateProgress()
				}
			}

			abstract fun apply()
		}

		inner class FileDetails : UiStateTest(true) {
			override fun apply() {
				itemView.isEnabled = true
				itemView.cloudFolderContent.visibility = GONE
				itemView.cloudFileContent.visibility = VISIBLE
				itemView.cloudFileText.visibility = VISIBLE
				itemView.cloudFileSubText.visibility = VISIBLE
				itemView.cloudFileProgress.visibility = GONE
				itemView.settings.visibility = VISIBLE
				itemView.itemCheckBox.visibility = GONE
			}
		}

		inner class FolderDetails : UiStateTest(false) {
			override fun apply() {
				itemView.isEnabled = true
				itemView.cloudFileContent.visibility = GONE
				itemView.cloudFolderContent.visibility = VISIBLE
				itemView.cloudFolderText.visibility = VISIBLE
				itemView.cloudFolderActionText.visibility = GONE
				itemView.settings.visibility = VISIBLE
				itemView.itemCheckBox.visibility = GONE
			}
		}

		inner class FileDeterminateProgress : UiStateTest(true) {
			override fun apply() {
				itemView.cloudFolderContent.visibility = GONE
				itemView.cloudFileContent.visibility = VISIBLE
				itemView.cloudFileText.visibility = VISIBLE
				itemView.cloudFileSubText.visibility = GONE
				itemView.cloudFileProgress.visibility = VISIBLE
				itemView.itemCheckBox.visibility = GONE
			}
		}

		inner class FileIndeterminateProgress : UiStateTest(true) {
			override fun apply() {
				itemView.cloudFolderContent.visibility = GONE
				itemView.cloudFileContent.visibility = VISIBLE
				itemView.cloudFileText.visibility = VISIBLE
				itemView.cloudFileSubText.visibility = VISIBLE
				itemView.cloudFileProgress.visibility = GONE
				itemView.itemCheckBox.visibility = GONE
			}

		}

		inner class FolderIndeterminateProgress : UiStateTest(false) {
			override fun apply() {
				itemView.cloudFileContent.visibility = GONE
				itemView.cloudFolderContent.visibility = VISIBLE
				itemView.cloudFolderText.visibility = VISIBLE
				itemView.cloudFolderActionText.visibility = VISIBLE
				itemView.itemCheckBox.visibility = GONE
			}
		}

		inner class FileSelection : UiStateTest(true) {
			override fun apply() {
				itemView.itemCheckBox.visibility = VISIBLE
				itemView.settings.visibility = GONE
			}
		}

		inner class FolderSelection : UiStateTest(false) {
			override fun apply() {
				itemView.itemCheckBox.visibility = VISIBLE
				itemView.settings.visibility = GONE
			}

		}
	}

	private fun isSelectable(folder: CloudFolderModel): Boolean {
		return chooseCloudNodeSettings?.selectionMode()?.allowsFolders() == true //
				&& chooseCloudNodeSettings?.excludeFolder(folder) == false
	}

	private fun isSelectable(file: CloudFileModel): Boolean {
		return chooseCloudNodeSettings?.selectionMode()?.allowsFiles() == true //
				&& chooseCloudNodeSettings?.namePattern()?.matcher(file.name)?.matches() == true
	}

	private fun isNavigationMode(navigationMode: ChooseCloudNodeSettings.NavigationMode): Boolean {
		return this.navigationMode == navigationMode
	}

	fun setSort(comparator: Comparator<CloudNodeModel<*>>) {
		updateComparator(comparator)
	}

	interface ItemClickListener {
		fun onFolderClicked(cloudFolderModel: CloudFolderModel)

		fun onFileClicked(cloudNodeModel: CloudFileModel)

		fun onNodeSettingsClicked(cloudNodeModel: CloudNodeModel<*>)

		fun onNodeLongClicked()

		fun onSelectedNodesChanged(selectedNodes: Int)
	}

	override fun getSectionName(position: Int): String {
		val node = all[position]

		if (node.isFolder) {
			return node.name.first().toString()
		}

		val formattedFileSize = fileSizeHelper.getFormattedFileSize((node as CloudFileModel).size)
		val formattedModifiedDate = dateHelper.getFormattedModifiedDate(node.modified)

		return when (comparator) {
			is CloudNodeModelDateNewestFirstComparator, is CloudNodeModelDateOldestFirstComparator -> formattedModifiedDate.orElse(node.name.first().toString())
			is CloudNodeModelSizeBiggestFirstComparator, is CloudNodeModelSizeSmallestFirstComparator -> formattedFileSize.orElse(node.name.first().toString())
			else -> all[position].name.first().toString()
		}
	}
}
