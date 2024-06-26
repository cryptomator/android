package org.cryptomator.presentation.ui.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.RelativeLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.cryptomator.domain.CloudNode
import org.cryptomator.generator.Fragment
import org.cryptomator.presentation.R
import org.cryptomator.presentation.R.dimen.global_padding
import org.cryptomator.presentation.databinding.FragmentBrowseFilesBinding
import org.cryptomator.presentation.intent.ChooseCloudNodeSettings
import org.cryptomator.presentation.intent.ChooseCloudNodeSettings.NavigationMode.BROWSE_FILES
import org.cryptomator.presentation.intent.ChooseCloudNodeSettings.NavigationMode.SELECT_ITEMS
import org.cryptomator.presentation.intent.ChooseCloudNodeSettings.SelectionMode.FILES_ONLY
import org.cryptomator.presentation.intent.ChooseCloudNodeSettings.SelectionMode.FOLDERS_ONLY
import org.cryptomator.presentation.model.CloudFileModel
import org.cryptomator.presentation.model.CloudFolderModel
import org.cryptomator.presentation.model.CloudNodeModel
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.presenter.BrowseFilesPresenter
import org.cryptomator.presentation.ui.adapter.BrowseFilesAdapter
import org.cryptomator.presentation.util.ResourceHelper.Companion.getPixelOffset
import java.util.Optional
import javax.inject.Inject

@Fragment
class BrowseFilesFragment : BaseFragment<FragmentBrowseFilesBinding>(FragmentBrowseFilesBinding::inflate) {

	@Inject
	lateinit var cloudNodesAdapter: BrowseFilesAdapter

	@Inject
	lateinit var browseFilesPresenter: BrowseFilesPresenter

	private var navigationMode: ChooseCloudNodeSettings.NavigationMode? = null

	private var filterText: String = ""

	var folder: CloudFolderModel
		get() = requireArguments().getSerializable(ARG_FOLDER) as CloudFolderModel
		set(updatedFolder) {
			arguments?.putSerializable(ARG_FOLDER, updatedFolder)
		}

	private val chooseCloudNodeSettings: ChooseCloudNodeSettings?
		get() = requireArguments().getSerializable(ARG_CHOOSE_CLOUD_NODE_SETTINGS) as ChooseCloudNodeSettings?

	private val refreshListener = SwipeRefreshLayout.OnRefreshListener { browseFilesPresenter.onRefreshTriggered(folder) }

	private val nodeClickListener = object : BrowseFilesAdapter.ItemClickListener {
		override fun onFolderClicked(cloudFolderModel: CloudFolderModel) {
			browseFilesPresenter.onFolderClicked(cloudFolderModel)
			filterText = ""
			browseFilesPresenter.invalidateOptionsMenu()
		}

		override fun onFileClicked(cloudNodeModel: CloudFileModel) {
			if (fileCanBeChosen(cloudNodeModel)) {
				browseFilesPresenter.onFileChosen(cloudNodeModel)
			} else {
				browseFilesPresenter.onFileClicked(cloudNodeModel)
			}
		}

		override fun onNodeSettingsClicked(cloudNodeModel: CloudNodeModel<*>) {
			browseFilesPresenter.onNodeSettingsClicked(cloudNodeModel)
		}

		override fun onNodeLongClicked() {
			browseFilesPresenter.onSelectionModeActivated()
		}

		override fun onSelectedNodesChanged(selectedNodes: Int) {
			browseFilesPresenter.onSelectedNodesChanged(selectedNodes)
		}
	}

	val selectedCloudNodes: List<CloudNodeModel<*>>
		get() = cloudNodesAdapter.selectedCloudNodes()

	override fun setupView() {
		setupNavigationMode()

		binding.floatingActionButton.floatingActionButton.setOnClickListener { browseFilesPresenter.onAddContentClicked() }
		binding.chooseLocationLayout.chooseLocationButton.setOnClickListener { browseFilesPresenter.onFolderChosen(folder) }

		binding.swipeRefreshLayout.setColorSchemeColors(ContextCompat.getColor(context(), R.color.colorPrimary))
		binding.swipeRefreshLayout.setOnRefreshListener(refreshListener)

		cloudNodesAdapter.setCallback(nodeClickListener)
		cloudNodesAdapter.setChooseCloudNodeSettings(chooseCloudNodeSettings)
		navigationMode?.let { cloudNodesAdapter.updateNavigationMode(it) }

		binding.recyclerViewLayout.recyclerView.layoutManager = LinearLayoutManager(context())
		binding.recyclerViewLayout.recyclerView.adapter = cloudNodesAdapter
		binding.recyclerViewLayout.recyclerView.setHasFixedSize(true)
		binding.recyclerViewLayout.recyclerView.setPadding(0, 0, 0, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 88f, resources.displayMetrics).toInt())
		binding.recyclerViewLayout.recyclerView.clipToPadding = false

		browseFilesPresenter.onFolderRedisplayed(folder)

		when {
			!hasCloudNodeSettings() -> setupViewForBrowseFilesMode()
			isSelectionMode(FOLDERS_ONLY) -> setupViewForFolderSelection()
			isSelectionMode(FILES_ONLY) -> setupViewForFilesSelection()
			isNavigationMode(SELECT_ITEMS) -> setupViewForNodeSelectionMode()
		}
	}

	private fun isNavigationMode(navigationMode: ChooseCloudNodeSettings.NavigationMode): Boolean = this.navigationMode == navigationMode

	private fun setupNavigationMode() {
		navigationMode = if (hasCloudNodeSettings()) {
			chooseCloudNodeSettings?.navigationMode()
		} else {
			BROWSE_FILES
		}
	}

	private fun setupViewForBrowseFilesMode() {
		showFloatingActionButton()
		binding.swipeRefreshLayout.isEnabled = true
	}

	private fun setupViewForNodeSelectionMode() {
		hideFloatingActionButton()
		disableSwipeRefresh()
	}

	private fun disableSwipeRefresh() {
		binding.swipeRefreshLayout.isRefreshing = false
		binding.swipeRefreshLayout.isEnabled = false
	}

	private fun setupViewForFilesSelection() {
		binding.chooseLocationLayout.extraTextAndButtonLayout.visibility = VISIBLE
		binding.chooseLocationLayout.chooseLocationButton.visibility = GONE
		binding.chooseLocationLayout.extraText.text = chooseCloudNodeSettings?.extraText()
		val layoutParams = binding.chooseLocationLayout.extraText.layoutParams as RelativeLayout.LayoutParams
		layoutParams.addRule(RelativeLayout.CENTER_HORIZONTAL)
		binding.chooseLocationLayout.extraText.layoutParams = layoutParams
		disableSwipeRefresh()
	}

	private fun setupViewForFolderSelection() {
		binding.chooseLocationLayout.extraTextAndButtonLayout.visibility = VISIBLE
		binding.chooseLocationLayout.chooseLocationButton.visibility = VISIBLE
		binding.chooseLocationLayout.chooseLocationButton.text = chooseCloudNodeSettings?.buttonText()
		binding.chooseLocationLayout.extraText.text = chooseCloudNodeSettings?.extraText()
		binding.chooseLocationLayout.extraText.setPadding(getPixelOffset(global_padding), 0, 0, 0)
		disableSwipeRefresh()
	}

	@SuppressLint("RestrictedApi") // Due to bug https://stackoverflow.com/questions/50343634/android-p-visibilityawareimagebutton-setvisibility-can-only-be-called-from-the-s
	private fun showFloatingActionButton() {
		binding.floatingActionButton.floatingActionButton.visibility = VISIBLE
	}

	@SuppressLint("RestrictedApi") // Due to bug https://stackoverflow.com/questions/50343634/android-p-visibilityawareimagebutton-setvisibility-can-only-be-called-from-the-s
	private fun hideFloatingActionButton() {
		binding.floatingActionButton.floatingActionButton.visibility = GONE
	}

	override fun loadContent() {
		browseFilesPresenter.onFolderDisplayed(folder)
	}

	override fun loadContentSilent() {
		browseFilesPresenter.onFolderReloadContent(folder)
	}

	fun show(nodes: List<CloudNodeModel<*>>?) {
		cloudNodesAdapter.clear()
		cloudNodesAdapter.addAll(cloudNodesAdapter.filterNodes(nodes, filterText))
		updateEmptyFolderHint()
	}

	fun showProgress(nodes: List<CloudNodeModel<*>>?, progress: ProgressModel?) {
		nodes?.forEach { node ->
			showProgress(node, progress)
		}
	}

	fun showProgress(node: CloudNodeModel<*>?, progress: ProgressModel?) {
		val viewHolder = viewHolderFor(node)
		if (viewHolder.isPresent) {
			viewHolder.get().showProgress(progress)
		} else {
			node?.progress = progress
			node?.let { addOrUpdate(it) }
		}
	}

	fun hideProgress(nodes: List<CloudNodeModel<*>>?) {
		nodes?.forEach { node ->
			hideProgress(node)
		}
	}

	fun hideProgress(cloudNode: CloudNodeModel<*>?) {
		val viewHolder = viewHolderFor(cloudNode)
		if (viewHolder.isPresent) {
			viewHolder.get().hideProgress()
		} else {
			cloudNode?.progress = ProgressModel.COMPLETED
			cloudNode?.let { addOrUpdate(it) }
		}
	}

	fun selectAllItems() {
		val hasUnSelectedNode = cloudNodesAdapter.hasUnSelectedNode()
		cloudNodesAdapter.renderedCloudNodes().forEach { node ->
			selectNode(node, hasUnSelectedNode)
		}
	}

	private fun selectNode(node: CloudNodeModel<*>, selected: Boolean) {
		val viewHolder = viewHolderFor(node)
		if (viewHolder.isPresent) {
			viewHolder.get().selectNode(selected)
		} else {
			node.isSelected = selected
			addOrUpdate(node)
		}
	}

	fun remove(cloudNode: List<CloudNodeModel<*>>?) {
		cloudNodesAdapter.deleteItems(cloudNode)
		updateEmptyFolderHint()
	}

	private fun viewHolderFor(nodeModel: CloudNodeModel<*>?): Optional<BrowseFilesAdapter.VaultContentViewHolder> {
		val positionOf = cloudNodesAdapter.positionOf(nodeModel)
		return Optional.ofNullable(binding.recyclerViewLayout.recyclerView.findViewHolderForAdapterPosition(positionOf) as? BrowseFilesAdapter.VaultContentViewHolder)
	}

	fun replaceRenamedCloudFile(cloudFile: CloudNodeModel<out CloudNode>) {
		cloudNodesAdapter.replaceRenamedCloudFile(cloudFile)
	}

	fun showLoading(loading: Boolean?) {
		loading?.let { binding.swipeRefreshLayout.isRefreshing = it }
	}

	fun addOrUpdate(cloudNode: CloudNodeModel<*>) {
		cloudNodesAdapter.addOrReplaceCloudNode(cloudNode)
		updateEmptyFolderHint()
	}

	private fun updateEmptyFolderHint() {
		binding.rlViewEmptyFolder.emptyFolderHint.visibility = if (cloudNodesAdapter.isEmpty) VISIBLE else GONE
	}

	private fun fileCanBeChosen(cloudFile: CloudFileModel): Boolean {
		val settings = chooseCloudNodeSettings
		return settings != null && settings.selectionMode().allowsFiles() && settings.namePattern().matcher(cloudFile.name).matches()
	}

	private fun hasCloudNodeSettings(): Boolean = chooseCloudNodeSettings != null

	private fun isSelectionMode(selectionMode: ChooseCloudNodeSettings.SelectionMode):
			Boolean = chooseCloudNodeSettings?.selectionMode() == selectionMode

	fun renderedCloudNodes(): List<CloudNodeModel<*>> = cloudNodesAdapter.renderedCloudNodes()

	fun rootView(): View = binding.slidingCoordinatorLayout

	fun navigationModeChanged(navigationMode: ChooseCloudNodeSettings.NavigationMode) {
		updateNavigationMode(navigationMode)

		if (navigationMode == SELECT_ITEMS) {
			setupViewForNodeSelectionMode()
		} else if (navigationMode == BROWSE_FILES) {
			setupViewForBrowseFilesMode()
		}
	}

	private fun updateNavigationMode(navigationMode: ChooseCloudNodeSettings.NavigationMode) {
		this.navigationMode = navigationMode
		cloudNodesAdapter.updateNavigationMode(navigationMode)
	}

	fun setFilterText(query: String) {
		filterText = query
	}

	fun setSort(comparator: Comparator<CloudNodeModel<*>>) {
		cloudNodesAdapter.setSort(comparator)
	}

	companion object {

		private const val ARG_FOLDER = "folder"
		private const val ARG_CHOOSE_CLOUD_NODE_SETTINGS = "chooseCloudNodeSettings"

		fun newInstance(folder: CloudFolderModel, chooseCloudNodeSettings: ChooseCloudNodeSettings?): BrowseFilesFragment {
			val result = BrowseFilesFragment()
			val args = Bundle()
			args.putSerializable(ARG_FOLDER, folder)
			args.putSerializable(ARG_CHOOSE_CLOUD_NODE_SETTINGS, chooseCloudNodeSettings)
			result.arguments = args
			return result
		}
	}

}
