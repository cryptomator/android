package org.cryptomator.presentation.ui.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.Menu
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.cryptomator.domain.CloudNode
import org.cryptomator.domain.exception.ParentFolderIsNullException
import org.cryptomator.generator.Activity
import org.cryptomator.generator.InjectIntent
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.ActivityLayoutBinding
import org.cryptomator.presentation.intent.BrowseFilesIntent
import org.cryptomator.presentation.intent.ChooseCloudNodeSettings
import org.cryptomator.presentation.intent.ChooseCloudNodeSettings.NavigationMode.BROWSE_FILES
import org.cryptomator.presentation.intent.ChooseCloudNodeSettings.NavigationMode.MOVE_CLOUD_NODE
import org.cryptomator.presentation.intent.ChooseCloudNodeSettings.NavigationMode.SELECT_ITEMS
import org.cryptomator.presentation.licensing.LicenseEnforcer
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
import org.cryptomator.presentation.service.CryptorsService
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
import org.cryptomator.presentation.ui.dialog.NoDirFileOrEmptyDialog
import org.cryptomator.presentation.ui.dialog.ReplaceDialog
import org.cryptomator.presentation.ui.dialog.SymLinkDialog
import org.cryptomator.presentation.ui.dialog.UploadCloudFileDialog
import org.cryptomator.presentation.ui.fragment.BrowseFilesFragment
import java.util.regex.Pattern
import javax.inject.Inject

@Activity
class BrowseFilesActivity : BaseActivity<ActivityLayoutBinding>(ActivityLayoutBinding::inflate), //
	BrowseFilesView, //
	BrowseFilesCallback, //
	ReplaceDialog.Callback, //
	FileNameDialog.Callback, //
	ConfirmDeleteCloudNodeDialog.Callback, //
	UploadCloudFileDialog.Callback,
	ExportCloudFilesDialog.Callback,
	SymLinkDialog.CallBack,
	NoDirFileOrEmptyDialog.CallBack,
	SearchView.OnQueryTextListener,
	SearchView.OnCloseListener {

	@Inject
	lateinit var browseFilesPresenter: BrowseFilesPresenter

	@Inject
	lateinit var licenseEnforcer: LicenseEnforcer

	@InjectIntent
	lateinit var browseFilesIntent: BrowseFilesIntent

	private var enableGeneralSelectionActions: Boolean = false

	private var navigationMode: ChooseCloudNodeSettings.NavigationMode? = null

	private var finishActivityDueToScreenLockEventReceiver: BroadcastReceiver? = null

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
		BrowseFilesFragment.newInstance(
			browseFilesIntent.folder(),
			browseFilesIntent.chooseCloudNodeSettings()
		)

	override fun onDestroy() {
		super.onDestroy()

		finishActivityDueToScreenLockEventReceiver?.let {
			LocalBroadcastManager.getInstance(this).unregisterReceiver(it)
		}
	}

	override fun onResume() {
		super.onResume()

		finishActivityDueToScreenLockEventReceiver = object : BroadcastReceiver() {
			override fun onReceive(context: Context, intent: Intent) {
				finish()
			}
		}.also { LocalBroadcastManager.getInstance(this).registerReceiver(it, IntentFilter(CryptorsService.SCREEN_AND_VAULT_LOCKED)) }
	}

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
				browseFilesFragment().folder.parent?.let {
					createBackStackFor(it)
				} ?: throw ParentFolderIsNullException(browseFilesFragment().folder.name)
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

	/**
	 * Handle toolbar/menu item selections and dispatch the corresponding presenter or fragment actions.
	 *
	 * Certain actions (delete, move, share) require write access and will be gated by the license enforcer before proceeding.
	 *
	 * @param itemId The selected menu item's resource id.
	 * @return `true` if the menu selection was handled, `false` otherwise.
	 */
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
			if (ensureWriteAccessForCurrentVault(LicenseEnforcer.LockedAction.DELETE_NODE)) {
				showConfirmDeleteNodeDialog(browseFilesFragment().selectedCloudNodes)
			}
			true
		}
		R.id.action_move_items -> {
			if (ensureWriteAccessForCurrentVault(LicenseEnforcer.LockedAction.MOVE_NODE)) {
				browseFilesPresenter.onMoveNodesClicked(
					folder, //
					browseFilesFragment().selectedCloudNodes as ArrayList<CloudNodeModel<*>>
				)
			}
			true
		}
		R.id.action_export_items -> {
			browseFilesPresenter.onExportNodesClicked( //
				browseFilesFragment().selectedCloudNodes as ArrayList<CloudNodeModel<*>>, //
				BrowseFilesPresenter.EXPORT_TRIGGERED_BY_USER
			)
			true
		}
		R.id.action_share_items -> {
			if (ensureWriteAccessForCurrentVault(LicenseEnforcer.LockedAction.SHARE_NODE)) {
				browseFilesPresenter.onShareNodesClicked(browseFilesFragment().selectedCloudNodes)
			}
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
		binding.mtToolbar.toolbar.title = effectiveTitle(browseFilesIntent.folder())
		binding.mtToolbar.toolbar.subtitle = effectiveSubtitle()
		setSupportActionBar(binding.mtToolbar.toolbar)
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
		val defaultTitle = browseFilesIntent.title() ?: getString(R.string.screen_file_browser_default_title)
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
			binding.mtToolbar.toolbar.setTitle(R.string.screen_file_browser_selection_mode_title_zero_elements)
		} else {
			binding.mtToolbar.toolbar.title = getString(R.string.screen_file_browser_selection_mode_title_one_or_more_elements, numberSelected)
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
		replaceFragment(
			BrowseFilesFragment.newInstance(
				folder,
				browseFilesIntent.chooseCloudNodeSettings()
			),
			FragmentAnimation.NAVIGATE_IN_TO_FOLDER
		)
	}

	override fun showAddContentDialog() {
		VaultContentActionBottomSheet.newInstance(browseFilesFragment().folder)
			.show(supportFragmentManager, "AddContentDialog")
	}

	override fun updateTitle(folder: CloudFolderModel) {
		binding.mtToolbar.toolbar.title = effectiveTitle(folder)
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

	/**
	 * Adds the given cloud node to the current list or updates it if already present.
	 *
	 * @param node The cloud node (file or folder) to add or update in the displayed list.
	 */
	override fun addOrUpdateCloudNode(node: CloudNodeModel<*>) {
		browseFilesFragment().addOrUpdate(node)
	}

	/**
	 * Initiates the create-folder flow for the current folder.
	 *
	 * Checks whether creating a folder is permitted in the current vault and, if permitted,
	 * displays the create-folder dialog.
	 */
	override fun onCreateNewFolderClicked() {
		if (ensureWriteAccessForCurrentVault(LicenseEnforcer.LockedAction.CREATE_FOLDER)) {
			showCreateFolderDialog()
		}
	}

	/**
	 * Displays the dialog used to create a new folder.
	 */
	private fun showCreateFolderDialog() {
		showDialog(CreateFolderDialog())
	}

	/**
	 * Initiates an upload into the given folder after verifying that the current vault allows write access.
	 *
	 * If write access is permitted, delegates the upload action to the presenter.
	 *
	 * @param folder The target folder to upload files into.
	 */
	override fun onUploadFilesClicked(folder: CloudFolderModel) {
		if (ensureWriteAccessForFolder(folder, LicenseEnforcer.LockedAction.UPLOAD_FILES)) {
			browseFilesPresenter.onUploadFilesClicked(folder)
		}
	}

	/**
	 * Initiates creation of a new text file in the current folder if the current vault permits writes.
	 *
	 * Checks write access for the current vault and proceeds with the text-file creation flow only when allowed.
	 */
	override fun onCreateNewTextFileClicked() {
		if (ensureWriteAccessForCurrentVault(LicenseEnforcer.LockedAction.CREATE_TEXT_FILE)) {
			browseFilesPresenter.onCreateNewTextFileClicked()
		}
	}

	/**
	 * Initiates the rename flow for the given cloud file if the current vault allows renaming.
	 *
	 * @param cloudFile The cloud file to rename.
	 */
	override fun onRenameFileClicked(cloudFile: CloudFileModel) {
		if (ensureWriteAccessForCurrentVault(LicenseEnforcer.LockedAction.RENAME_NODE)) {
			onRenameCloudNodeClicked(cloudFile)
		}
	}

	/**
	 * Initiates the rename flow for the given folder when write access to its vault is permitted.
	 *
	 * @param cloudFolderModel The folder model to rename; used as the target for the rename dialog/action.
	 */
	override fun onRenameFolderClicked(cloudFolderModel: CloudFolderModel) {
		if (ensureWriteAccessForCurrentVault(LicenseEnforcer.LockedAction.RENAME_NODE)) {
			onRenameCloudNodeClicked(cloudFolderModel)
		}
	}

	/**
	 * Shows a rename dialog for the given cloud node.
	 *
	 * @param cloudNodeModel The cloud node (file or folder) to rename; the dialog will be pre-filled with its current name.
	 */
	private fun onRenameCloudNodeClicked(cloudNodeModel: CloudNodeModel<*>) {
		showDialog(CloudNodeRenameDialog.newInstance(cloudNodeModel))
	}

	/**
	 * Shows a confirmation dialog to delete the provided cloud node when the current vault allows write access.
	 *
	 * @param cloudFile The cloud node to be deleted; presented in the confirmation dialog if deletion is permitted.
	 */
	override fun onDeleteNodeClicked(cloudFile: CloudNodeModel<*>) {
		if (ensureWriteAccessForCurrentVault(LicenseEnforcer.LockedAction.DELETE_NODE)) {
			showConfirmDeleteNodeDialog(listOf(cloudFile))
		}
	}

	/**
	 * Initiates sharing of the given cloud file if the current vault allows sharing.
	 *
	 * @param cloudFile The cloud file to be shared. 
	 */
	override fun onShareFileClicked(cloudFile: CloudFileModel) {
		if (ensureWriteAccessForCurrentVault(LicenseEnforcer.LockedAction.SHARE_NODE)) {
			browseFilesPresenter.onShareFileClicked(cloudFile)
		}
	}

	/**
	 * Initiates moving the given cloud file within the current folder if write access is allowed.
	 *
	 * @param cloudFile The cloud file to move.
	 */
	override fun onMoveFileClicked(cloudFile: CloudFileModel) {
		if (ensureWriteAccessForCurrentVault(LicenseEnforcer.LockedAction.MOVE_NODE)) {
			browseFilesPresenter.onMoveNodeClicked(folder, cloudFile)
		}
	}

	/**
	 * Handle a user request to open an existing text file in the default external text editor.
	 *
	 * @param cloudFile The cloud file to open.
	 */
	override fun onOpenWithTextFileClicked(cloudFile: CloudFileModel) {
		browseFilesPresenter.onOpenWithTextFileClicked(cloudFile, newlyCreated = false, internalEditor = false)
	}

	/**
	 * Shows a confirmation dialog for deleting the given cloud nodes.
	 *
	 * @param nodes The cloud nodes to be deleted if the user confirms.
	 */
	private fun showConfirmDeleteNodeDialog(nodes: List<CloudNodeModel<*>>) {
		showDialog(ConfirmDeleteCloudNodeDialog.newInstance(nodes))
	}

	/**
	 * Initiates moving the given folder into the current folder when write access to the current vault is permitted.
	 *
	 * @param cloudFolderModel The folder to move.
	 */
	override fun onMoveFolderClicked(cloudFolderModel: CloudFolderModel) {
		if (ensureWriteAccessForCurrentVault(LicenseEnforcer.LockedAction.MOVE_NODE)) {
			browseFilesPresenter.onMoveNodeClicked(folder, cloudFolderModel)
		}
	}

	/**
	 * Creates a back stack entry to navigate out to the given parent folder.
	 *
	 * Replaces the current fragment with a BrowseFilesFragment for the provided folder and
	 * applies the "navigate out of folder" animation.
	 *
	 * @param sourceParent The parent folder to navigate back to.
	 */
	private fun createBackStackFor(sourceParent: CloudFolderModel) {
		replaceFragment(
			BrowseFilesFragment.newInstance(
				sourceParent,
				browseFilesIntent.chooseCloudNodeSettings()
			),
			FragmentAnimation.NAVIGATE_OUT_OF_FOLDER,
			false
		)
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

	/**
 * Gets the current BrowseFilesFragment from the fragment container.
 *
 * @return The active BrowseFilesFragment instance.
 */
private fun browseFilesFragment(): BrowseFilesFragment = getCurrentFragment(R.id.fragment_container) as BrowseFilesFragment

	/**
	 * Determine whether the current folder's vault allows the specified write-protected action.
	 *
	 * @param action The write-protected action to check (one of `LicenseEnforcer.LockedAction`).
	 * @return `true` if write access is granted for the current folder's vault for the provided action, `false` otherwise.
	 */
	private fun ensureWriteAccessForCurrentVault(action: LicenseEnforcer.LockedAction): Boolean {
		return ensureWriteAccessForFolder(browseFilesFragment().folder, action)
	}

	/**
	 * Checks whether write access is allowed for the vault that contains the given folder.
	 *
	 * If `folder` is `null`, the current fragment folder is used as the target.
	 *
	 * @param folder The folder whose vault write access should be verified, or `null` to use the current folder.
	 * @param action The write-capable action to check permission for.
	 * @return `true` if write access for the target folder's vault is allowed for the specified action, `false` otherwise.
	 */
	private fun ensureWriteAccessForFolder(folder: CloudFolderModel?, action: LicenseEnforcer.LockedAction): Boolean {
		val targetFolder = folder ?: browseFilesFragment().folder
		return licenseEnforcer.ensureWriteAccessForVault(this, targetFolder.vault(), action)
	}

	/**
	 * Creates a new text file in the currently displayed folder with the given name.
	 *
	 * @param fileName The desired name for the new text file within the current folder.
	 */
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
		updateFilter(query)
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

	override fun showNoDirFileOrEmptyDialog(cryptoFolderName: String, cloudFolderPath: String) {
		showDialog(NoDirFileOrEmptyDialog.newInstance(cryptoFolderName, cloudFolderPath))
	}

	override fun updateActiveFolderDueToAuthenticationProblem(folder: CloudFolderModel) {
		browseFilesFragment().folder = folder
	}

	override fun navigateFolderBackBecauseSymlink() {
		onBackPressed()
	}

	override fun navigateFolderBackBecauseNoDirFile() {
		onBackPressed()
	}
}
