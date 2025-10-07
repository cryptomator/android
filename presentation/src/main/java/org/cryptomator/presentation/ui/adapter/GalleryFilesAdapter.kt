package org.cryptomator.presentation.ui.adapter

import android.graphics.BitmapFactory
import android.os.PatternMatcher
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView
import org.cryptomator.domain.CloudNode
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.ItemGalleryFilesNodeBinding
import org.cryptomator.presentation.intent.ChooseCloudNodeSettings
import org.cryptomator.presentation.intent.ChooseCloudNodeSettings.NavigationMode.BROWSE_FILES
import org.cryptomator.presentation.intent.ChooseCloudNodeSettings.NavigationMode.SELECT_ITEMS
import org.cryptomator.presentation.model.CloudFileModel
import org.cryptomator.presentation.model.CloudFolderModel
import org.cryptomator.presentation.model.CloudNodeModel
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.model.ProgressStateModel.Companion.COMPLETED
import org.cryptomator.presentation.model.comparator.CloudNodeModelDateNewestFirstComparator
import org.cryptomator.presentation.model.comparator.CloudNodeModelDateOldestFirstComparator
import org.cryptomator.presentation.model.comparator.CloudNodeModelSizeBiggestFirstComparator
import org.cryptomator.presentation.model.comparator.CloudNodeModelSizeSmallestFirstComparator
import org.cryptomator.presentation.ui.adapter.GalleryFilesAdapter.GalleryContentViewHolder
import org.cryptomator.presentation.util.DateHelper
import org.cryptomator.presentation.util.FileIcon
import org.cryptomator.presentation.util.FileSizeHelper
import org.cryptomator.presentation.util.FileUtil
import org.cryptomator.presentation.util.ResourceHelper.Companion.getDrawable
import org.cryptomator.util.SharedPreferencesHandler
import org.cryptomator.util.file.MimeType
import org.cryptomator.util.file.MimeTypes
import javax.inject.Inject

class GalleryFilesAdapter @Inject
constructor(
	private val dateHelper: DateHelper, //
	private val fileSizeHelper: FileSizeHelper, //
	private val fileUtil: FileUtil, //
	private val sharedPreferencesHandler: SharedPreferencesHandler, //
	private val mimeTypes: MimeTypes //
) : RecyclerViewBaseAdapter<CloudNodeModel<*>, GalleryFilesAdapter.ItemClickListener, GalleryContentViewHolder, ItemGalleryFilesNodeBinding>(CloudNodeModelDateNewestFirstComparator()),
	FastScrollRecyclerView.SectionedAdapter {

	private var chooseCloudNodeSettings: ChooseCloudNodeSettings? = null
	private var navigationMode: ChooseCloudNodeSettings.NavigationMode? = null

	private val isInSelectionMode: Boolean
		get() = chooseCloudNodeSettings != null

	override fun createViewHolder(binding: ItemGalleryFilesNodeBinding, viewType: Int): GalleryContentViewHolder {
		return GalleryContentViewHolder(binding)
	}

	override fun getItemBinding(inflater: LayoutInflater, parent: ViewGroup?, viewType: Int): ItemGalleryFilesNodeBinding {
		return ItemGalleryFilesNodeBinding.inflate(inflater, parent, false)
	}

	fun addOrReplaceCloudNode(cloudNodeModel: CloudNodeModel<*>) {
		if (contains(cloudNodeModel)) {
			replaceItem(cloudNodeModel)
		} else {
			addItem(cloudNodeModel)
		}
	}

	fun triggerUpdateSelectedNodesNumberInfo() {
		callback.onSelectedNodesChanged(selectedCloudNodes().size)
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
				nodes?.filter { cloudNode -> cloudNode.name.contains(filterText, true) }
			}
		} else {
			nodes
		}
	}

	// descritto da R.layout.item_gallery_files_node
	// sono state importate le sue componenti
	// kotlinx.android.synthetic.main.item_gallery_files_node.view.galleryCloudNodeImage
	inner class GalleryContentViewHolder internal constructor(private val binding: ItemGalleryFilesNodeBinding) : RecyclerViewBaseAdapter<*, *, *, *>.ItemViewHolder(binding.root) {

		private var uiState: UiStateTest? = null

		private var currentProgressIcon: Int = 0

		private var bound: CloudNodeModel<*>? = null

		override fun bind(position: Int) {
			bound = getItem(position)
			bound?.let { internalBind(it) }
		}

		private fun internalBind(node: CloudNodeModel<*>) {
			clearPreviousHolderSelection()
			bindNodeImage(node)
			bindLongNodeClick(node)
			bindFileOrFolder(node)
		}

		private fun clearPreviousHolderSelection() {
			// durante il rebind sta probabilmente riutilizzando lo stesso oggetto grafico (itemView)
			// di un precente cloudNode che era stato selezionato
			// e.g. se l'item 22 viene selezionato, cambia il foreground e quando viene
			// ribindato con l'indice 0 rimane il foregound sbagliato!
			binding.galleryItemContainer.foreground = null
		}

		private fun bindNodeImage(node: CloudNodeModel<*>) {
			if (node is CloudFileModel && isImageMediaType(node.name) && node.thumbnail != null) {
				val bitmap = BitmapFactory.decodeFile(node.thumbnail!!.absolutePath)
				binding.galleryCloudNodeImage.setImageBitmap(bitmap)
			} else {
				binding.galleryCloudNodeImage.setImageResource(bindCloudNodeImage(node))
			}
		}

		private fun isImageMediaType(filename: String): Boolean {
			return (mimeTypes.fromFilename(filename) ?: MimeType.WILDCARD_MIME_TYPE).mediatype == "image"
		}

		private fun bindCloudNodeImage(cloudNodeModel: CloudNodeModel<*>): Int {
			if (cloudNodeModel is CloudFileModel) {
				return FileIcon.fileIconFor(cloudNodeModel.name, fileUtil).iconResource
			} else if (cloudNodeModel is CloudFolderModel) {
				return R.drawable.node_folder
			}
			throw IllegalStateException("Could not identify the CloudNodeModel type")
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
			enableNodeClick { callback.onFileClicked(file) }
		}

		private fun bindFileSelectionModeIfPresent(file: CloudFileModel) {
			if (isInSelectionMode) {
				disableNodeLongClick()
				if (!isSelectable(file)) {
					binding.galleryItemContainer.isEnabled = false
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
			node.progress?.let { showProgress(it) }
		}

		private fun bindFolder(folder: CloudFolderModel) {
//			itemView.cloudFolderText.text = folder.name
			enableNodeClick { callback.onFolderClicked(folder) }
		}

		private fun bindFolderSelectionModeIfPresent(folder: CloudFolderModel) {
			if (isInSelectionMode) {
				disableNodeLongClick()
//				hideSettings()
				if (!isSelectable(folder)) {
					itemView.isEnabled = false
				}
			}
		}

		private fun bindNodeSelection(cloudNodeModel: CloudNodeModel<*>) {
			// this method is invoked for each item to be displayed!

//			itemView.galleryItemContainer.setOnLongClickListener { /* https://stackoverflow.com/a/12230526
//				As you may know, the View hierarchy in Android is represented by a tree.
//				When you return true from the onItemLongClick() - it means that the View that
//				currently received the event is the true event receiver and the event should
//				not be propagated to the other Views in the tree; when you return false -
//				you let the event be passed to the other Views that may consume it.
//				 */
//				toggleSelection(cloudNodeModel)
//				true
//			}

			enableNodeClick {
				toggleSelection(cloudNodeModel)
			}

			// first set
			if (cloudNodeModel.isSelected) {
				binding.galleryItemContainer.foreground = getDrawable(R.drawable.rectangle_selection_mode)
				triggerUpdateSelectedNodesNumberInfo()
			}
		}

		private fun toggleSelection(cloudNodeModel: CloudNodeModel<*>) {
			// toggle selection
			cloudNodeModel.isSelected = !cloudNodeModel.isSelected

			// toggle rectangle
			if (cloudNodeModel.isSelected)
				binding.galleryItemContainer.foreground = getDrawable(R.drawable.rectangle_selection_mode)
			else
				binding.galleryItemContainer.foreground = null

			// update screen info
			triggerUpdateSelectedNodesNumberInfo()
		}

		fun showProgress(progress: ProgressModel?) {
			bound?.progress = progress
			when {
				progress?.state() === COMPLETED -> hideProgress()
				progress?.progress() == ProgressModel.UNKNOWN_PROGRESS_PERCENTAGE -> showIndeterminateProgress(progress)
				progress?.state() !== COMPLETED -> progress?.let { showDeterminateProgress(it) }
			}
		}

		private fun showIndeterminateProgress(progress: ProgressModel) {
			uiState?.let { switchTo(it.indeterminateProgress()) }
			if (uiState?.isForFile == true) {
//				itemView.cloudFileSubText.setText(progress.state().textResourceId())
			} else {
//				itemView.cloudFolderActionText.setText(progress.state().textResourceId())
			}

			if (!progress.state().isSelectable) {
				disableNodeActions()
			}
		}

		private fun disableNodeActions() {
			itemView.isEnabled = false
//			itemView.settings.visibility = GONE
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
				binding.rlCloudFileProgress.cloudFile.progress = progress.progress()
				if (currentProgressIcon != progress.state().imageResourceId()) {
					currentProgressIcon = progress.state().imageResourceId()
					binding.progressIcon.setImageDrawable(getDrawable(currentProgressIcon))
				}
			} else {
				// no determinate progress for folders
//				itemView.cloudFolderActionText.setText(progress.state().textResourceId())
			}
		}

		fun hideProgress() {
			uiState?.let { switchTo(it.details()) }
			bound?.progress = null
		}

		private fun switchTo(state: UiStateTest) {
			if (uiState !== state) {
				uiState = state
				uiState?.apply()
			}
		}

		fun selectNode(checked: Boolean) {
			if (checked)
				binding.galleryItemContainer.foreground = getDrawable(R.drawable.rectangle_selection_mode)
			else
				binding.galleryItemContainer.foreground = null

			bound?.let { it.isSelected = checked }
			triggerUpdateSelectedNodesNumberInfo()
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
//				itemView.cloudFolderContent.visibility = GONE
//				itemView.cloudFileContent.visibility = VISIBLE
//				itemView.cloudFileText.visibility = VISIBLE
//				itemView.cloudFileSubText.visibility = VISIBLE
				binding.cloudFileProgress.visibility = GONE
//				itemView.settings.visibility = VISIBLE
//				itemView.itemCheckBox.visibility = GONE
			}
		}

		inner class FolderDetails : UiStateTest(false) {

			override fun apply() {
				itemView.isEnabled = true
//				itemView.cloudFileContent.visibility = GONE
//				itemView.cloudFolderContent.visibility = VISIBLE
//				itemView.cloudFolderText.visibility = VISIBLE
//				itemView.cloudFolderActionText.visibility = GONE
//				itemView.settings.visibility = VISIBLE
//				itemView.itemCheckBox.visibility = GONE
			}
		}

		inner class FileDeterminateProgress : UiStateTest(true) {

			override fun apply() {
//				itemView.cloudFolderContent.visibility = GONE
//				itemView.cloudFileContent.visibility = VISIBLE
//				itemView.cloudFileText.visibility = VISIBLE
//				itemView.cloudFileSubText.visibility = GONE
				binding.cloudFileProgress.visibility = VISIBLE
//				itemView.itemCheckBox.visibility = GONE
			}
		}

		inner class FileIndeterminateProgress : UiStateTest(true) {

			override fun apply() {
//				itemView.cloudFolderContent.visibility = GONE
//				itemView.cloudFileContent.visibility = VISIBLE
//				itemView.cloudFileText.visibility = VISIBLE
//				itemView.cloudFileSubText.visibility = VISIBLE
				binding.cloudFileProgress.visibility = GONE
//				itemView.itemCheckBox.visibility = GONE
			}

		}

		inner class FolderIndeterminateProgress : UiStateTest(false) {

			override fun apply() {
//				itemView.cloudFileContent.visibility = GONE
//				itemView.cloudFolderContent.visibility = VISIBLE
//				itemView.cloudFolderText.visibility = VISIBLE
//				itemView.cloudFolderActionText.visibility = VISIBLE
//				itemView.itemCheckBox.visibility = GONE
			}
		}

		inner class FileSelection : UiStateTest(true) {

			override fun apply() {
//				itemView.itemCheckBox.visibility = VISIBLE
//				itemView.settings.visibility = GONE
			}
		}

		inner class FolderSelection : UiStateTest(false) {

			override fun apply() {
//				itemView.itemCheckBox.visibility = VISIBLE
//				itemView.settings.visibility = GONE
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

		node as CloudFileModel
		val formattedFileSize = fileSizeHelper.getFormattedFileSize((node).size)
		val formattedModifiedDate = dateHelper.getModifiedDate((node).modified)

		return when (comparator) {
			is CloudNodeModelDateNewestFirstComparator, is CloudNodeModelDateOldestFirstComparator -> formattedModifiedDate ?: node.name.first().toString()
			is CloudNodeModelSizeBiggestFirstComparator, is CloudNodeModelSizeSmallestFirstComparator -> formattedFileSize ?: node.name.first().toString()
			else -> all[position].name.first().toString()
		}
	}
}
