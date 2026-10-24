package org.cryptomator.presentation.ui.adapter

import android.os.PatternMatcher
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView
import org.cryptomator.domain.CachedVaultFile
import org.cryptomator.domain.CloudNode
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.ItemBrowseFilesNodeBinding
import org.cryptomator.presentation.intent.ChooseCloudNodeSettings
import org.cryptomator.presentation.intent.ChooseCloudNodeSettings.NavigationMode.BROWSE_FILES
import org.cryptomator.presentation.intent.ChooseCloudNodeSettings.NavigationMode.SELECT_ITEMS
import org.cryptomator.presentation.model.CloudFileModel
import org.cryptomator.presentation.model.CloudFolderModel
import org.cryptomator.presentation.model.CloudNodeModel
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.model.ProgressStateModel
import org.cryptomator.presentation.model.ProgressStateModel.Companion.COMPLETED
import org.cryptomator.presentation.model.comparator.CloudNodeModelDateNewestFirstComparator
import org.cryptomator.presentation.model.comparator.CloudNodeModelDateOldestFirstComparator
import org.cryptomator.presentation.model.comparator.CloudNodeModelNameAZComparator
import org.cryptomator.presentation.model.comparator.CloudNodeModelSizeBiggestFirstComparator
import org.cryptomator.presentation.model.comparator.CloudNodeModelSizeSmallestFirstComparator
import org.cryptomator.presentation.ui.adapter.BrowseFilesAdapter.VaultContentViewHolder
import org.cryptomator.presentation.util.DateHelper
import org.cryptomator.presentation.util.FileIcon
import org.cryptomator.presentation.util.FileSizeHelper
import org.cryptomator.presentation.util.FileUtil
import org.cryptomator.presentation.util.ResourceHelper.Companion.getDrawable
import org.cryptomator.presentation.util.ThumbnailLoader
import org.cryptomator.util.SharedPreferencesHandler
import org.cryptomator.util.file.MimeTypes
import javax.inject.Inject

class BrowseFilesAdapter @Inject
constructor(
	private val dateHelper: DateHelper, //
	private val fileSizeHelper: FileSizeHelper, //
	private val fileUtil: FileUtil, //
	private val sharedPreferencesHandler: SharedPreferencesHandler, //
	private val thumbnailLoader: ThumbnailLoader, //
	private val mimeTypes: MimeTypes
) : RecyclerViewBaseAdapter<CloudNodeModel<*>, BrowseFilesAdapter.ItemClickListener, VaultContentViewHolder, ItemBrowseFilesNodeBinding>(CloudNodeModelNameAZComparator()), FastScrollRecyclerView.SectionedAdapter {

	private var chooseCloudNodeSettings: ChooseCloudNodeSettings? = null
	private var navigationMode: ChooseCloudNodeSettings.NavigationMode? = null

	private val isInSelectionMode: Boolean
		get() = chooseCloudNodeSettings != null

	override fun createViewHolder(binding: ItemBrowseFilesNodeBinding, viewType: Int): VaultContentViewHolder {
		return VaultContentViewHolder(binding)
	}

	override fun getItemBinding(inflater: LayoutInflater, parent: ViewGroup?, viewType: Int): ItemBrowseFilesNodeBinding {
		return ItemBrowseFilesNodeBinding.inflate(inflater, parent, false)
	}

	fun addOrReplaceCloudNode(cloudNodeModel: CloudNodeModel<*>) {
		if (contains(cloudNodeModel)) {
			replaceItem(cloudNodeModel)
		} else {
			addItem(cloudNodeModel)
		}
	}

	/**
	 * Shows [nodes] instead of the current ones, updating only the rows that changed. A row that didn't change keeps its
	 * node, so that its selection and progress stay with what is bound; a changed row takes them over. The details of the
	 * other rows are shown again anyway, since the age of the modified date they show changes with time.
	 */
	fun replaceAll(nodes: List<CloudNodeModel<*>>) {
		val previous = ArrayList(itemCollection)
		val previousByPath = previous.associateBy { it.javaClass to it.path }
		val next = nodes.map { node -> rowNode(previousByPath[node.javaClass to node.path], node) }.sortedWith(comparator)
		val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
			override fun getOldListSize(): Int = previous.size
			override fun getNewListSize(): Int = next.size
			override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
				return previous[oldItemPosition].javaClass == next[newItemPosition].javaClass && previous[oldItemPosition].path == next[newItemPosition].path
			}

			override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean = previous[oldItemPosition] === next[newItemPosition]
		})
		itemCollection.clear()
		itemCollection.addAll(next)
		diff.dispatchUpdatesTo(this)
		notifyItemRangeChanged(0, next.size, DETAILS_PAYLOAD)
	}

	override fun onBindViewHolder(holder: VaultContentViewHolder, position: Int, payloads: MutableList<Any>) {
		if (payloads.isNotEmpty() && payloads.all { it === DETAILS_PAYLOAD }) {
			holder.showDetailsAgain()
		} else {
			super.onBindViewHolder(holder, position, payloads)
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
				nodes?.filter { cloudNode -> cloudNode.name.contains(filterText, true) }
			}
		} else {
			nodes
		}
	}

	inner class VaultContentViewHolder internal constructor(private val binding: ItemBrowseFilesNodeBinding) : RecyclerViewBaseAdapter<CloudNodeModel<*>, BrowseFilesAdapter.ItemClickListener, VaultContentViewHolder, ItemBrowseFilesNodeBinding>.ItemViewHolder(binding.root) {

		private var uiState: UiStateTest? = null

		private var currentProgressIcon: Int = 0

		private var bound: CloudNodeModel<*>? = null

		private var shownDetails: String? = null

		override fun bind(position: Int) {
			bound = getItem(position)
			bound?.let { internalBind(it) }
		}

		/**
		 * Shows the size and modified date of the bound file again, unless something else replaced them meanwhile, e.g. a
		 * progress or a failed upload.
		 */
		fun showDetailsAgain() {
			val file = bound as? CloudFileModel ?: return
			if (binding.llCloudFileContent.cloudFileSubText.text.toString() == shownDetails) {
				showDetails(file)
			}
		}

		private fun showDetails(file: CloudFileModel) {
			val details = fileDetails(file)
			binding.llCloudFileContent.cloudFileSubText.text = details
			shownDetails = details
		}

		private fun internalBind(node: CloudNodeModel<*>) {
			bindNodeImage(node)
			bindSettings(node)
			bindLongNodeClick(node)
			bindFileOrFolder(node)
		}

		private fun bindNodeImage(node: CloudNodeModel<*>) {
			val image = (node as? CloudFileModel)?.takeIf { mimeTypes.isImage(it.name) }
			val thumbnail = image?.thumbnail
			if (thumbnail != null) {
				thumbnailLoader.load(thumbnail, binding.cloudNodeImage, bindCloudNodeImage(node))
			} else {
				binding.cloudNodeImage.setTag(R.id.thumbnail_key, null)
				binding.cloudNodeImage.setImageResource(bindCloudNodeImage(node))
				if (image?.toCloudNode() is CachedVaultFile) {
					callback.onThumbnailMissing(image)
				}
			}
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
			binding.settings.setOnClickListener { callback.onNodeSettingsClicked(node) }
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
			bindUploadIfPresent(file)
			bindProgressIfPresent(file)
			bindSelectItemsModeIfPresent(file)
			bindFileSelectionModeIfPresent(file)
		}

		/**
		 * A file whose upload failed is uploaded again when tapped. A waiting or running upload keeps the row's actions
		 * usable, so that the file can be deleted meanwhile.
		 */
		private fun bindUploadIfPresent(file: CloudFileModel) {
			when (file.upload) {
				CachedVaultFile.Upload.PENDING -> if (file.progress == null) {
					file.progress = ProgressModel(ProgressStateModel.UPLOAD_PENDING)
				}
				CachedVaultFile.Upload.FAILED -> {
					binding.llCloudFileContent.cloudFileSubText.setText(R.string.screen_file_browser_upload_failed)
					enableNodeClick { callback.onRetryUploadClicked(file) }
				}
				CachedVaultFile.Upload.NONE -> {}
			}
		}

		private fun bindFile(file: CloudFileModel) {
			binding.llCloudFileContent.cloudFileText.text = file.name
			showDetails(file)

			enableNodeClick { callback.onFileClicked(file) }
		}

		private fun bindFileSelectionModeIfPresent(file: CloudFileModel) {
			if (isInSelectionMode) {
				disableNodeLongClick()
				hideSettings()
				if (!isSelectable(file)) {
					binding.llCloudFileContent.cloudFileSubText.visibility = GONE
					binding.llCloudFileContent.cloudFileSubText.text = ""
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
			node.progress?.let { showProgress(it) }
		}

		private fun bindFolder(folder: CloudFolderModel) {
			binding.llCloudFolderContent.cloudFolderText.text = folder.name
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
			binding.settings.visibility = GONE
		}

		private fun bindNodeSelection(cloudNodeModel: CloudNodeModel<*>) {
			binding.itemCheckBox.setOnCheckedChangeListener { _, isChecked ->
				cloudNodeModel.isSelected = isChecked
				callback.onSelectedNodesChanged(selectedCloudNodes().size)
			}
			enableNodeClick { binding.itemCheckBox.toggle() }

			binding.itemCheckBox.isChecked = cloudNodeModel.isSelected
		}

		private fun fileDetails(cloudFile: CloudFileModel): String {
			val formattedFileSize = fileSizeHelper.getFormattedFileSize(cloudFile.size)
			val formattedModifiedDate = dateHelper.getFormattedModifiedDate(cloudFile.modified)

			return if (formattedFileSize != null) {
				if (formattedModifiedDate != null) {
					"$formattedFileSize • $formattedModifiedDate"
				} else {
					formattedFileSize
				}
			} else formattedModifiedDate ?: ""
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
				binding.llCloudFileContent.cloudFileSubText.setText(progress.state().textResourceId())
			} else {
				binding.llCloudFolderContent.cloudFolderActionText.setText(progress.state().textResourceId())
			}

			if (!progress.state().isSelectable) {
				disableNodeActions()
			}
		}

		private fun disableNodeActions() {
			itemView.isEnabled = false
			binding.settings.visibility = GONE
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
				if (!isUploadProgress(progress)) {
					disableNodeActions()
				}
				binding.llCloudFileContent.rlCloudFileProgress.cloudFile.progress = progress.progress()
				if (currentProgressIcon != progress.state().imageResourceId()) {
					currentProgressIcon = progress.state().imageResourceId()
					binding.llCloudFileContent.progressIcon.setImageDrawable(getDrawable(currentProgressIcon))
				}
			} else {
				// no determinate progress for folders
				binding.llCloudFolderContent.cloudFolderActionText.setText(progress.state().textResourceId())
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
			binding.itemCheckBox.isChecked = checked
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
				binding.llCloudFolderContent.cloudFolderContent.visibility = GONE
				binding.llCloudFileContent.cloudFileContent.visibility = VISIBLE
				binding.llCloudFileContent.cloudFileText.visibility = VISIBLE
				binding.llCloudFileContent.cloudFileSubText.visibility = VISIBLE
				binding.llCloudFileContent.cloudFileProgress.visibility = GONE
				binding.settings.visibility = VISIBLE
				binding.itemCheckBox.visibility = GONE
			}
		}

		inner class FolderDetails : UiStateTest(false) {

			override fun apply() {
				itemView.isEnabled = true
				binding.llCloudFileContent.cloudFileContent.visibility = GONE
				binding.llCloudFolderContent.cloudFolderContent.visibility = VISIBLE
				binding.llCloudFolderContent.cloudFolderText.visibility = VISIBLE
				binding.llCloudFolderContent.cloudFolderActionText.visibility = GONE
				binding.settings.visibility = VISIBLE
				binding.itemCheckBox.visibility = GONE
			}
		}

		inner class FileDeterminateProgress : UiStateTest(true) {

			override fun apply() {
				binding.llCloudFolderContent.cloudFolderContent.visibility = GONE
				binding.llCloudFileContent.cloudFileContent.visibility = VISIBLE
				binding.llCloudFileContent.cloudFileText.visibility = VISIBLE
				binding.llCloudFileContent.cloudFileSubText.visibility = GONE
				binding.llCloudFileContent.cloudFileProgress.visibility = VISIBLE
				binding.itemCheckBox.visibility = GONE
			}
		}

		inner class FileIndeterminateProgress : UiStateTest(true) {

			override fun apply() {
				binding.llCloudFolderContent.cloudFolderContent.visibility = GONE
				binding.llCloudFileContent.cloudFileContent.visibility = VISIBLE
				binding.llCloudFileContent.cloudFileText.visibility = VISIBLE
				binding.llCloudFileContent.cloudFileSubText.visibility = VISIBLE
				binding.llCloudFileContent.cloudFileProgress.visibility = GONE
				binding.itemCheckBox.visibility = GONE
			}

		}

		inner class FolderIndeterminateProgress : UiStateTest(false) {

			override fun apply() {
				binding.llCloudFileContent.cloudFileContent.visibility = GONE
				binding.llCloudFolderContent.cloudFolderContent.visibility = VISIBLE
				binding.llCloudFolderContent.cloudFolderText.visibility = VISIBLE
				binding.llCloudFolderContent.cloudFolderActionText.visibility = VISIBLE
				binding.itemCheckBox.visibility = GONE
			}
		}

		inner class FileSelection : UiStateTest(true) {

			override fun apply() {
				binding.itemCheckBox.visibility = VISIBLE
				binding.settings.visibility = GONE
			}
		}

		inner class FolderSelection : UiStateTest(false) {

			override fun apply() {
				binding.itemCheckBox.visibility = VISIBLE
				binding.settings.visibility = GONE
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

		fun onRetryUploadClicked(cloudFile: CloudFileModel)

		/**
		 * Called for each image of a vault that is shown without its thumbnail.
		 */
		fun onThumbnailMissing(cloudFile: CloudFileModel)
	}

	override fun getSectionName(position: Int): String {
		val node = all[position]

		if (node.isFolder) {
			return node.name.first().toString()
		}

		val formattedFileSize = fileSizeHelper.getFormattedFileSize((node as CloudFileModel).size)
		val formattedModifiedDate = dateHelper.getFormattedModifiedDate(node.modified)

		return when (comparator) {
			is CloudNodeModelDateNewestFirstComparator, is CloudNodeModelDateOldestFirstComparator -> formattedModifiedDate ?: node.name.first().toString()
			is CloudNodeModelSizeBiggestFirstComparator, is CloudNodeModelSizeSmallestFirstComparator -> formattedFileSize ?: node.name.first().toString()
			else -> all[position].name.first().toString()
		}
	}

	private companion object {

		/**
		 * Shows only the details of a row again, see [VaultContentViewHolder.showDetailsAgain], without the animation of a
		 * changed row.
		 */
		val DETAILS_PAYLOAD = Any()
	}
}
