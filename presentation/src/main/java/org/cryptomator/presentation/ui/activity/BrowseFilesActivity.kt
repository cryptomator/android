package org.cryptomator.presentation.ui.activity

import android.content.Intent
import android.view.Menu
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import org.cryptomator.domain.CloudNode
import org.cryptomator.generator.Activity
import org.cryptomator.generator.InjectIntent
import org.cryptomator.presentation.R
import org.cryptomator.presentation.intent.BrowseFilesIntent
import org.cryptomator.presentation.intent.ChooseCloudNodeSettings
import org.cryptomator.presentation.intent.ChooseCloudNodeSettings.NavigationMode.BROWSE_FILES
import org.cryptomator.presentation.intent.ChooseCloudNodeSettings.NavigationMode.MOVE_CLOUD_NODE
import org.cryptomator.presentation.intent.ChooseCloudNodeSettings.NavigationMode.SELECT_ITEMS
import org.cryptomator.presentation.model.CloudFileModel
import org.cryptomator.presentation.model.CloudFolderModel
import org.cryptomator.presentation.model.CloudNodeModel
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.model.ProgressModel.Companion.COMPLETED
import org.cryptomator.presentation.model.comparator.CloudNodeModelDateNewestFirstComparator
import org.cryptomator.presentation.model.comparator.CloudNodeModelDateOldestFirstComparator
import org.cryptomator.presentation.model.comparator.CloudNodeModelNameAZComparator
import org.cryptomator.presentation.model.comparator.CloudNodeModelNameZAComparator
import org.cryptomator.presentation.model.comparator.CloudNodeModelSizeBiggestFirstComparator
import org.cryptomator.presentation.model.comparator.CloudNodeModelSizeSmallestFirstComparator
import org.cryptomator.presentation.presenter.BrowseFilesPresenter
import org.cryptomator.presentation.presenter.BrowseFilesPresenter.Companion.OPEN_FILE_FINISHED
import org.cryptomator.presentation.ui.activity.view.BrowseFilesView
import org.cryptomator.presentation.ui.bottomsheet.FileSettingsBottomSheet
import org.cryptomator.presentation.ui.bottomsheet.FolderSettingsBottomSheet
import org.cryptomator.presentation.ui.bottomsheet.VaultContentActionBottomSheet
import org.cryptomator.presentation.ui.callback.BrowseFilesCallback
import org.cryptomator.presentation.ui.dialog.CloudNodeRenameDialog
import org.cryptomator.presentation.ui.dialog.ConfirmDeleteCloudNodeDialog
import org.cryptomator.presentation.ui.dialog.CreateFolderDialog
import org.cryptomator.presentation.ui.dialog.ExportCloudFilesDialog
import org.cryptomator.presentation.ui.dialog.FileNameDialog
import org.cryptomator.presentation.ui.dialog.FileTypeNotSupportedDialog
import org.cryptomator.presentation.ui.dialog.NoDirFileDialog
import org.cryptomator.presentation.ui.dialog.ReplaceDialog
import org.cryptomator.presentation.ui.dialog.SymLinkDialog
import org.cryptomator.presentation.ui.dialog.UploadCloudFileDialog
import org.cryptomator.presentation.ui.fragment.BrowseFilesFragment
import java.util.ArrayList
import java.util.Locale
import java.util.regex.Pattern
import javax.inject.Inject
import kotlinx.android.synthetic.main.toolbar_layout.toolbar

@Activity
class BrowseFilesActivity : BaseActivity(), //
		BrowseFilesView, //
		BrowseFilesCallback, //
		ReplaceDialog.Callback, //
		FileNameDialog.Callback, //
		ConfirmDeleteCloudNodeDialog.Callback, //
		UploadCloudFileDialog.Callback,
		ExportCloudFilesDialog.Callback,
		SymLinkDialog.CallBack,
		NoDirFileDialog.CallBack,
		SearchView.OnQueryTextListener,
		SearchView.OnCloseListener {

	@Inject
	lateinit var browseFilesPresenter: BrowseFilesPresenter

	@InjectIntent
	lateinit var browseFilesIntent: BrowseFilesIntent

	private var enableGeneralSelectionActions: Boolean = false

	private var navigationMode: ChooseCloudNodeSettings.NavigationMode? = null

	override fun setupView() {
		setupToolbar()
		setupNavigationMode()
	}

	private fun setupNavigationMode() {
		navigationMode = if (hasCloudNodeSettings()) {
			browseFilesIntent.chooseCloudNodeSettings().navigationMode()
		} else {
			BROWSE_FILES
		}
	}

	override fun onWindowFocusChanged(hasFocus: Boolean) {
		super.onWindowFocusChanged(hasFocus)
		browseFilesPresenter.onWindowFocusChanged(hasFocus)
	}

	override fun snackbarView(): View = browseFilesFragment().rootView()

	override val folder: CloudFolderModel
		get() = browseFilesFragment().folder

	override fun createFragment(): Fragment =
			BrowseFilesFragment.newInstance(browseFilesIntent.folder(),
					browseFilesIntent.chooseCloudNodeSettings())

	override fun onBackPressed() {
		browseFilesPresenter.onBackPressed()
		when {
			isNavigationMode(SELECT_ITEMS) -> {
				browseFilesPresenter.disableSelectionMode()
			}
			supportFragmentManager.backStackEntryCount > 0 -> {
				supportFragmentManager.popBackStack()
			}
			hasCloudNodeSettings() && isNavigationMode(MOVE_CLOUD_NODE) && browseFilesFragment().folder.hasParent() -> {
				createBackStackFor(browseFilesFragment().folder.parent)
			}
			else -> {
				super.onBackPressed()
			}
		}
	}

	private fun isNavigationMode(navigationMode: ChooseCloudNodeSettings.NavigationMode): Boolean = this.navigationMode == navigationMode

	private fun hasCloudNodeSettings(): Boolean =
			browseFilesIntent.chooseCloudNodeSettings() != null

	override fun getCustomMenuResource(): Int {
		return when {
			isNavigationMode(SELECT_ITEMS) -> {
				R.menu.menu_file_browser_selection_mode
			}
			hasCloudNodeSettings() &&
					browseFilesIntent.chooseCloudNodeSettings().selectionMode().allowsFolders() -> {
				R.menu.menu_file_browser_select_folder
			}
			else -> {
				R.menu.menu_file_browser
			}
		}
	}

	override fun onMenuItemSelected(itemId: Int): Boolean = when (itemId) {
		R.id.action_create_folder -> {
			showCreateFolderDialog()
			true
		}
		R.id.action_select_items -> {
			browseFilesPresenter.onSelectionModeActivated()
			true
		}
		R.id.action_refresh -> {
			browseFilesPresenter.onRefreshTriggered(browseFilesFragment().folder)
			true
		}
		R.id.action_select_all_items -> {
			browseFilesFragment().selectAllItems()
			true
		}
		R.id.action_delete_items -> {
			showConfirmDeleteNodeDialog(browseFilesFragment().selectedCloudNodes)
			true
		}
		R.id.action_move_items -> {
			browseFilesPresenter.onMoveNodesClicked(folder, //
					browseFilesFragment().selectedCloudNodes as ArrayList<CloudNodeModel<*>>)
			true
		}
		R.id.action_export_items -> {
			browseFilesPresenter.onExportNodesClicked( //
					browseFilesFragment().selectedCloudNodes as ArrayList<CloudNodeModel<*>>, //
					BrowseFilesPresenter.EXPORT_TRIGGERED_BY_USER)
			true
		}
		R.id.action_share_items -> {
			browseFilesPresenter.onShareNodesClicked(browseFilesFragment().selectedCloudNodes)
			true
		}
		R.id.action_sort_az -> {
			browseFilesFragment().setSort(CloudNodeModelNameAZComparator())
			browseFilesPresenter.onRefreshTriggered(browseFilesFragment().folder)
			true
		}
		R.id.action_sort_za -> {
			browseFilesFragment().setSort(CloudNodeModelNameZAComparator())
			browseFilesPresenter.onRefreshTriggered(browseFilesFragment().folder)
			true
		}
		R.id.action_sort_newest -> {
			browseFilesFragment().setSort(CloudNodeModelDateNewestFirstComparator())
			browseFilesPresenter.onRefreshTriggered(browseFilesFragment().folder)
			true
		}
		R.id.action_sort_oldest -> {
			browseFilesFragment().setSort(CloudNodeModelDateOldestFirstComparator())
			browseFilesPresenter.onRefreshTriggered(browseFilesFragment().folder)
			true
		}
		R.id.action_sort_biggest -> {
			browseFilesFragment().setSort(CloudNodeModelSizeBiggestFirstComparator())
			browseFilesPresenter.onRefreshTriggered(browseFilesFragment().folder)
			true
		}
		R.id.action_sort_smallest -> {
			browseFilesFragment().setSort(CloudNodeModelSizeSmallestFirstComparator())
			browseFilesPresenter.onRefreshTriggered(browseFilesFragment().folder)
			true
		}
		android.R.id.home -> {
			// Respond to the action bar's Up/Home button
			if (isNavigationMode(SELECT_ITEMS)) {
				browseFilesPresenter.disableSelectionMode()
			} else {
				// finish this activity and does not call the onCreate method of the parent activity
				finish()
			}
			super.onMenuItemSelected(itemId)
		}
		else -> super.onMenuItemSelected(itemId)
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
		super.onActivityResult(requestCode, resultCode, intent)

		if (requestCode == OPEN_FILE_FINISHED) {
			browseFilesPresenter.openFileFinished()
		}
	}

	override fun onPrepareOptionsMenu(menu: Menu): Boolean {
		if (isNavigationMode(SELECT_ITEMS)) {
			menu.findItem(R.id.action_delete_items).isEnabled = enableGeneralSelectionActions
			menu.findItem(R.id.action_move_items).isEnabled = enableGeneralSelectionActions
			menu.findItem(R.id.action_export_items).isEnabled = enableGeneralSelectionActions
			menu.findItem(R.id.action_share_items).isEnabled = enableGeneralSelectionActions
		}

		val searchView = menu.findItem(R.id.action_search).actionView as SearchView
		searchView.setOnQueryTextListener(this)
		searchView.setOnCloseListener(this)

		return super.onPrepareOptionsMenu(menu)
	}

	private fun setupToolbar() {
		toolbar.title = effectiveTitle(browseFilesIntent.folder())
		toolbar.subtitle = effectiveSubtitle()
		setSupportActionBar(toolbar)
		if (hasCloudNodeSettings()) {
			effectiveToolbarIcon(browseFilesIntent.chooseCloudNodeSettings().extraToolbarIcon())
		}
	}

	private fun effectiveToolbarIcon(extraToolbarIcon: Int) {
		supportActionBar?.let {
			if (extraToolbarIcon != ChooseCloudNodeSettings.NO_ICON) {
				it.setDisplayHomeAsUpEnabled(true)
				it.setHomeAsUpIndicator(extraToolbarIcon)
			}
		}
	}

	private fun hideToolbarIcon() {
		supportActionBar?.setDisplayHomeAsUpEnabled(false)
	}

	private fun effectiveTitle(folder: CloudFolderModel?): String {
		val defaultTitle = getString(R.string.screen_file_browser_default_title)
		return folder?.name?.let { folderName ->
			if (folderName.isNotEmpty()) {
				folderName
			} else {
				defaultTitle
			}
		} ?: defaultTitle
	}

	private fun effectiveSubtitle(): String? {
		return if (browseFilesIntent.chooseCloudNodeSettings() == null) {
			null
		} else {
			browseFilesIntent.chooseCloudNodeSettings().extraTitle()
		}
	}

	override fun showFileTypeNotSupportedDialog(file: CloudFileModel) {
		showDialog(FileTypeNotSupportedDialog.newInstance(file))
	}

	override fun showReplaceDialog(existingFiles: List<String>, size: Int) {
		ReplaceDialog.withContext(this).show(existingFiles, size)
	}

	override fun showUploadDialog(uploadingFiles: Int) {
		showDialog(UploadCloudFileDialog.newInstance(uploadingFiles))
	}

	override fun renderedCloudNodes(): List<CloudNodeModel<*>> = browseFilesFragment().renderedCloudNodes()

	override fun onCreateFolderClick(folderName: String) {
		browseFilesPresenter.onCreateFolderPressed(browseFilesFragment().folder, folderName)
	}

	override fun onExportFileClicked(cloudFile: CloudFileModel) {
		browseFilesPresenter.onExportFileClicked(cloudFile, BrowseFilesPresenter.EXPORT_TRIGGERED_BY_USER)
	}

	override fun onExportFileAfterAppChooserClicked(cloudFile: CloudFileModel) {
		browseFilesPresenter.onExportFileClicked(cloudFile, BrowseFilesPresenter.EXPORT_AFTER_APP_CHOOSER)
	}

	override fun onExportCancelled() {
		browseFilesPresenter.exportNodesCanceled()
	}

	private fun currentFolderPath(): String {
		val currentFolder = browseFilesFragment().folder
		return currentFolder.vault()?.let { it.path + currentFolder.path } ?: currentFolder.path
	}

	override fun onReplacePositiveClicked() {
		browseFilesPresenter.uploadFilesAndReplaceExistingFiles()
	}

	override fun onReplaceNegativeClicked() {
		browseFilesPresenter.uploadFilesAndSkipExistingFiles()
	}

	override fun onShareFolderClicked(cloudFolderModel: CloudFolderModel) {
		browseFilesPresenter.onShareFolderClicked(cloudFolderModel)
	}

	override fun onExportFolderClicked(cloudFolderModel: CloudFolderModel) {
		browseFilesPresenter.onExportFolderClicked(cloudFolderModel, BrowseFilesPresenter.EXPORT_TRIGGERED_BY_USER)
	}

	override fun onReplaceCanceled() {
		showProgress(COMPLETED)
	}

	override fun showNodeSettingsDialog(node: CloudNodeModel<*>) {
		val cloudNodeSettingDialog: DialogFragment = if (node.isFolder) {
			FolderSettingsBottomSheet.newInstance(node as CloudFolderModel, currentFolderPath())
		} else {
			FileSettingsBottomSheet.newInstance(node as CloudFileModel, currentFolderPath())
		}
		cloudNodeSettingDialog.show(supportFragmentManager, "CloudNodeSettings")
	}

	override fun disableGeneralSelectionActions() {
		enableGeneralSelectionActions = false
	}

	override fun enableGeneralSelectionActions() {
		enableGeneralSelectionActions = true
	}

	override fun enableSelectionMode() {
		changeNavigationMode(SELECT_ITEMS)
		showSelectionMode()
	}

	private fun showSelectionMode() {
		updateSelectionTitle(0)
		effectiveToolbarIcon(R.drawable.ic_clear)
		invalidateOptionsMenu()
	}

	override fun updateSelectionTitle(numberSelected: Int) {
		if (numberSelected == 0) {
			toolbar.title = getString(R.string.screen_file_browser_selection_mode_title_zero_elements)
		} else {
			toolbar.title = getString(R.string.screen_file_browser_selection_mode_title_one_or_more_elements, numberSelected)
		}
	}

	override fun disableSelectionMode() {
		changeNavigationMode(BROWSE_FILES)
		hideSelectionMode()
		disableAllSelectionActions()
	}

	private fun disableAllSelectionActions() {
		enableGeneralSelectionActions = false
	}

	private fun hideSelectionMode() {
		updateTitle(folder)
		hideToolbarIcon()
		invalidateOptionsMenu()
	}

	private fun changeNavigationMode(navigationMode: ChooseCloudNodeSettings.NavigationMode) {
		this.navigationMode = navigationMode
		triggerNavigationModeChanged()
	}

	private fun triggerNavigationModeChanged() {
		navigationMode?.let { browseFilesFragment().navigationModeChanged(it) }
	}

	override fun navigateTo(folder: CloudFolderModel) {
		replaceFragment(BrowseFilesFragment.newInstance(folder,
				browseFilesIntent.chooseCloudNodeSettings()),
				FragmentAnimation.NAVIGATE_IN_TO_FOLDER)
	}

	override fun showAddContentDialog() {
		VaultContentActionBottomSheet.newInstance(browseFilesFragment().folder)
				.show(supportFragmentManager, "AddContentDialog")
	}

	override fun updateTitle(folder: CloudFolderModel) {
		toolbar.title = effectiveTitle(folder)
	}

	override fun hasExcludedFolder(): Boolean {
		browseFilesFragment().renderedCloudNodes().forEach { cloudNodeModel ->
			browseFilesIntent.chooseCloudNodeSettings().excludeFolderContainingNames.forEach { name ->
				if (Pattern.compile(Pattern.quote(name)).matcher(cloudNodeModel.name).matches()) {
					return true
				}
			}
		}
		return false
	}

	override fun showCloudNodes(nodes: List<CloudNodeModel<*>>) {
		browseFilesFragment().show(nodes)
	}

	override fun addOrUpdateCloudNode(node: CloudNodeModel<*>) {
		browseFilesFragment().addOrUpdate(node)
	}

	override fun onCreateNewFolderClicked() {
		showCreateFolderDialog()
	}

	private fun showCreateFolderDialog() {
		showDialog(CreateFolderDialog())
	}

	override fun onUploadFilesClicked(folder: CloudFolderModel) {
		browseFilesPresenter.onUploadFilesClicked(folder)
	}

	override fun onCreateNewTextFileClicked() {
		browseFilesPresenter.onCreateNewTextFileClicked()
	}

	override fun onRenameFileClicked(cloudFile: CloudFileModel) {
		onRenameCloudNodeClicked(cloudFile)
	}

	override fun onRenameFolderClicked(cloudFolderModel: CloudFolderModel) {
		onRenameCloudNodeClicked(cloudFolderModel)
	}

	private fun onRenameCloudNodeClicked(cloudNodeModel: CloudNodeModel<*>) {
		showDialog(CloudNodeRenameDialog.newInstance(cloudNodeModel))
	}

	override fun onDeleteNodeClicked(cloudFile: CloudNodeModel<*>) {
		showConfirmDeleteNodeDialog(listOf(cloudFile))
	}

	override fun onShareFileClicked(cloudFile: CloudFileModel) {
		browseFilesPresenter.onShareFileClicked(cloudFile)
	}

	override fun onMoveFileClicked(cloudFile: CloudFileModel) {
		browseFilesPresenter.onMoveNodeClicked(folder, cloudFile)
	}

	override fun onOpenWithTextFileClicked(cloudFile: CloudFileModel) {
		browseFilesPresenter.onOpenWithTextFileClicked(cloudFile, newlyCreated = false, internalEditor = false)
	}

	private fun showConfirmDeleteNodeDialog(nodes: List<CloudNodeModel<*>>) {
		showDialog(ConfirmDeleteCloudNodeDialog.newInstance(nodes))
	}

	override fun onMoveFolderClicked(cloudFolderModel: CloudFolderModel) {
		browseFilesPresenter.onMoveNodeClicked(folder, cloudFolderModel)
	}

	private fun createBackStackFor(sourceParent: CloudFolderModel) {
		replaceFragment(BrowseFilesFragment.newInstance(sourceParent,
				browseFilesIntent.chooseCloudNodeSettings()),
				FragmentAnimation.NAVIGATE_OUT_OF_FOLDER,
				false)
	}

	override fun onRenameCloudNodeClicked(cloudNodeModel: CloudNodeModel<*>, newCloudNodeName: String) {
		browseFilesPresenter.onRenameCloudNodePressed(cloudNodeModel, newCloudNodeName)
	}

	override fun deleteCloudNodesFromAdapter(nodes: List<CloudNodeModel<*>>) {
		browseFilesFragment().remove(nodes)
	}

	override fun replaceRenamedCloudNode(node: CloudNodeModel<out CloudNode>) {
		browseFilesFragment().replaceRenamedCloudFile(node)
	}

	override fun showProgress(node: CloudNodeModel<*>, progress: ProgressModel) {
		browseFilesFragment().showProgress(node, progress)
	}

	override fun showProgress(nodes: List<CloudNodeModel<*>>, progress: ProgressModel) {
		browseFilesFragment().showProgress(nodes, progress)
	}

	override fun hideProgress(node: CloudNodeModel<*>) {
		browseFilesFragment().hideProgress(node)
	}

	override fun hideProgress(nodes: List<CloudNodeModel<*>>) {
		browseFilesFragment().hideProgress(nodes)
	}

	override fun showLoading(loading: Boolean) {
		browseFilesFragment().showLoading(loading)
	}

	private fun browseFilesFragment(): BrowseFilesFragment = getCurrentFragment(R.id.fragmentContainer) as BrowseFilesFragment

	override fun onCreateNewTextFileClicked(fileName: String) {
		browseFilesPresenter.onCreateNewTextFileClicked(browseFilesFragment().folder, fileName)
	}

	override fun onDeleteCloudNodeConfirmed(nodes: List<CloudNodeModel<*>>) {
		browseFilesPresenter.onDeleteCloudNodes(nodes)
		if (isNavigationMode(SELECT_ITEMS)) {
			browseFilesPresenter.disableSelectionMode()
		}
	}

	override fun onUploadCanceled() {
		browseFilesPresenter.onUploadCanceled()
	}

	override fun onQueryTextSubmit(query: String?): Boolean {
		updateFilter(query?.toLowerCase(Locale.getDefault()))
		return false
	}

	override fun onQueryTextChange(query: String?): Boolean {
		if (sharedPreferencesHandler.useLiveSearch()) {
			updateFilter(query)
		}
		return false
	}

	private fun updateFilter(query: String?) {
		showLoading(true)
		browseFilesFragment().setFilterText(query.orEmpty())
		browseFilesPresenter.onFolderReloadContent(folder)
	}

	override fun onClose(): Boolean {
		updateFilter(String())
		return false
	}

	override fun showSymLinkDialog() {
		showDialog(SymLinkDialog.newInstance())
	}

	override fun showNoDirFileDialog(cryptoFolderName: String, cloudFolderPath: String) {
		showDialog(NoDirFileDialog.newInstance(cryptoFolderName, cloudFolderPath))
	}

	override fun navigateFolderBackBecauseSymlink() {
		onBackPressed()
	}

	override fun navigateFolderBackBecauseNoDirFile() {
		onBackPressed()
	}
}
