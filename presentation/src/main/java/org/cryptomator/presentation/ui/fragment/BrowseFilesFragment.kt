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
import kotlinx.android.synthetic.main.floating_action_button_layout.floatingActionButton
import kotlinx.android.synthetic.main.fragment_browse_files.slidingCoordinatorLayout
import kotlinx.android.synthetic.main.fragment_browse_files.swipeRefreshLayout
import kotlinx.android.synthetic.main.recycler_view_layout.recyclerView
import kotlinx.android.synthetic.main.view_browses_files_extra_text_and_button.chooseLocationButton
import kotlinx.android.synthetic.main.view_browses_files_extra_text_and_button.extraText
import kotlinx.android.synthetic.main.view_browses_files_extra_text_and_button.extraTextAndButtonLayout
import kotlinx.android.synthetic.main.view_empty_folder.emptyFolderHint

@Fragment(R.layout.fragment_browse_files)
open class BrowseFilesFragment : BaseFragment(), FilesFragmentInterface {

	@Inject
	lateinit var cloudNodesAdapter: BrowseFilesAdapter

	@Inject
	lateinit var browseFilesPresenter: BrowseFilesPresenter

	private var navigationMode: ChooseCloudNodeSettings.NavigationMode? = null

	private var filterText: String = ""

	override var folder: CloudFolderModel
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

	override val selectedCloudNodes: List<CloudNodeModel<*>>
		get() = cloudNodesAdapter.selectedCloudNodes()

	override fun setupView() {
		setupNavigationMode()

		floatingActionButton.setOnClickListener { browseFilesPresenter.onAddContentClicked() }
		chooseLocationButton.setOnClickListener { browseFilesPresenter.onFolderChosen(folder) }

		swipeRefreshLayout.setColorSchemeColors(ContextCompat.getColor(context(), R.color.colorPrimary))
		swipeRefreshLayout.setOnRefreshListener(refreshListener)

		cloudNodesAdapter.setCallback(nodeClickListener)
		cloudNodesAdapter.setChooseCloudNodeSettings(chooseCloudNodeSettings)
		navigationMode?.let { cloudNodesAdapter.updateNavigationMode(it) }

		recyclerView.layoutManager = LinearLayoutManager(context())
//		recyclerView.layoutManager = GridLayoutManager(context(), 2)
		recyclerView.adapter = cloudNodesAdapter
		recyclerView.setHasFixedSize(true)
		recyclerView.setPadding(0, 0, 0, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 88f, resources.displayMetrics).toInt())
		recyclerView.clipToPadding = false

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
		swipeRefreshLayout.isEnabled = true
	}

	private fun setupViewForNodeSelectionMode() {
		hideFloatingActionButton()
		disableSwipeRefresh()
	}

	private fun disableSwipeRefresh() {
		swipeRefreshLayout.isRefreshing = false
		swipeRefreshLayout.isEnabled = false
	}

	private fun setupViewForFilesSelection() {
		extraTextAndButtonLayout.visibility = VISIBLE
		chooseLocationButton.visibility = GONE
		extraText.text = chooseCloudNodeSettings?.extraText()
		val layoutParams = extraText.layoutParams as RelativeLayout.LayoutParams
		layoutParams.addRule(RelativeLayout.CENTER_HORIZONTAL)
		extraText?.layoutParams = layoutParams
		disableSwipeRefresh()
	}

	private fun setupViewForFolderSelection() {
		extraTextAndButtonLayout?.visibility = VISIBLE
		chooseLocationButton.visibility = VISIBLE
		chooseLocationButton.text = chooseCloudNodeSettings?.buttonText()
		extraText.text = chooseCloudNodeSettings?.extraText()
		extraText.setPadding(getPixelOffset(global_padding), 0, 0, 0)
		disableSwipeRefresh()
	}

	@SuppressLint("RestrictedApi") // Due to bug https://stackoverflow.com/questions/50343634/android-p-visibilityawareimagebutton-setvisibility-can-only-be-called-from-the-s
	private fun showFloatingActionButton() {
		floatingActionButton.visibility = VISIBLE
	}

	@SuppressLint("RestrictedApi") // Due to bug https://stackoverflow.com/questions/50343634/android-p-visibilityawareimagebutton-setvisibility-can-only-be-called-from-the-s
	private fun hideFloatingActionButton() {
		floatingActionButton.visibility = GONE
	}

	override fun loadContent() {
		browseFilesPresenter.onFolderDisplayed(folder)
	}

	override fun loadContentSilent() {
		browseFilesPresenter.onFolderReloadContent(folder)
	}

	override fun show(nodes: List<CloudNodeModel<*>>?) {
		cloudNodesAdapter.clear()
		cloudNodesAdapter.addAll(cloudNodesAdapter.filterNodes(nodes, filterText))
		updateEmptyFolderHint()
	}

	override fun showProgress(nodes: List<CloudNodeModel<*>>?, progress: ProgressModel?) {
		nodes?.forEach { node ->
			showProgress(node, progress)
		}
	}

	override fun showProgress(node: CloudNodeModel<*>?, progress: ProgressModel?) {
		val viewHolder = viewHolderFor(node)
		if (viewHolder.isPresent) {
			viewHolder.get().showProgress(progress)
		} else {
			node?.progress = progress
			node?.let { addOrUpdate(it) }
		}
	}

	override fun hideProgress(nodes: List<CloudNodeModel<*>>?) {
		nodes?.forEach { node ->
			hideProgress(node)
		}
	}

	override fun hideProgress(cloudNode: CloudNodeModel<*>?) {
		val viewHolder = viewHolderFor(cloudNode)
		if (viewHolder.isPresent) {
			viewHolder.get().hideProgress()
		} else {
			cloudNode?.progress = ProgressModel.COMPLETED
			cloudNode?.let { addOrUpdate(it) }
		}
	}

	override fun selectAllItems() {
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

	override fun remove(cloudNode: List<CloudNodeModel<*>>?) {
		cloudNodesAdapter.deleteItems(cloudNode)
		updateEmptyFolderHint()
	}

	private fun viewHolderFor(nodeModel: CloudNodeModel<*>?): Optional<BrowseFilesAdapter.VaultContentViewHolder> {
		val positionOf = cloudNodesAdapter.positionOf(nodeModel)
		return Optional.ofNullable(recyclerView.findViewHolderForAdapterPosition(positionOf) as? BrowseFilesAdapter.VaultContentViewHolder)
	}

	override fun replaceRenamedCloudFile(cloudFile: CloudNodeModel<out CloudNode>) {
		cloudNodesAdapter.replaceRenamedCloudFile(cloudFile)
	}

	override fun showLoading(loading: Boolean?) {
		loading?.let { swipeRefreshLayout.isRefreshing = it }
	}

	override fun addOrUpdate(cloudNode: CloudNodeModel<*>) {
		cloudNodesAdapter.addOrReplaceCloudNode(cloudNode)
		updateEmptyFolderHint()
	}

	private fun updateEmptyFolderHint() {
		emptyFolderHint.visibility = if (cloudNodesAdapter.isEmpty) VISIBLE else GONE
	}

	private fun fileCanBeChosen(cloudFile: CloudFileModel): Boolean {
		val settings = chooseCloudNodeSettings
		return settings != null && settings.selectionMode().allowsFiles() && settings.namePattern().matcher(cloudFile.name).matches()
	}

	private fun hasCloudNodeSettings(): Boolean = chooseCloudNodeSettings != null

	private fun isSelectionMode(selectionMode: ChooseCloudNodeSettings.SelectionMode):
			Boolean = chooseCloudNodeSettings?.selectionMode() == selectionMode

	override fun renderedCloudNodes(): List<CloudNodeModel<*>> = cloudNodesAdapter.renderedCloudNodes()

	override fun rootView(): View = slidingCoordinatorLayout

	override fun navigationModeChanged(navigationMode: ChooseCloudNodeSettings.NavigationMode) {
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

	override fun setFilterText(query: String) {
		filterText = query
	}

	override fun setSort(comparator: Comparator<CloudNodeModel<*>>) {
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
