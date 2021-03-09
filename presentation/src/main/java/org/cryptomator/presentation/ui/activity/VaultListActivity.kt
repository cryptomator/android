package org.cryptomator.presentation.ui.activity

import android.content.Intent
import android.net.Uri
import android.view.View
import androidx.fragment.app.Fragment
import org.cryptomator.domain.Vault
import org.cryptomator.generator.Activity
import org.cryptomator.generator.InjectIntent
import org.cryptomator.presentation.CryptomatorApp
import org.cryptomator.presentation.R
import org.cryptomator.presentation.intent.Intents.browseFilesIntent
import org.cryptomator.presentation.intent.Intents.settingsIntent
import org.cryptomator.presentation.intent.VaultListIntent
import org.cryptomator.presentation.model.CloudFolderModel
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.model.VaultModel
import org.cryptomator.presentation.presenter.VaultListPresenter
import org.cryptomator.presentation.service.OpenWritableFileNotification
import org.cryptomator.presentation.ui.activity.view.VaultListView
import org.cryptomator.presentation.ui.bottomsheet.AddVaultBottomSheet
import org.cryptomator.presentation.ui.bottomsheet.SettingsVaultBottomSheet
import org.cryptomator.presentation.ui.callback.VaultListCallback
import org.cryptomator.presentation.ui.dialog.AskForLockScreenDialog
import org.cryptomator.presentation.ui.dialog.BetaConfirmationDialog
import org.cryptomator.presentation.ui.dialog.UpdateAppAvailableDialog
import org.cryptomator.presentation.ui.dialog.UpdateAppDialog
import org.cryptomator.presentation.ui.dialog.VaultDeleteConfirmationDialog
import org.cryptomator.presentation.ui.dialog.VaultNotFoundDialog
import org.cryptomator.presentation.ui.dialog.VaultRenameDialog
import org.cryptomator.presentation.ui.fragment.VaultListFragment
import org.cryptomator.presentation.ui.layout.ObscuredAwareCoordinatorLayout.Listener
import java.util.Locale
import javax.inject.Inject
import kotlinx.android.synthetic.main.activity_layout_obscure_aware.activityRootView
import kotlinx.android.synthetic.main.toolbar_layout.toolbar

@Activity(layout = R.layout.activity_layout_obscure_aware)
class VaultListActivity : BaseActivity(), //
		VaultListView, //
		VaultListCallback, //
		AskForLockScreenDialog.Callback, //
		VaultNotFoundDialog.Callback, //
		UpdateAppAvailableDialog.Callback, //
		UpdateAppDialog.Callback, //
		BetaConfirmationDialog.Callback {

	@Inject
	lateinit var vaultListPresenter: VaultListPresenter

	@InjectIntent
	lateinit var vaultListIntent: VaultListIntent

	override fun onWindowFocusChanged(hasFocus: Boolean) {
		super.onWindowFocusChanged(hasFocus)
		vaultListPresenter.onWindowFocusChanged(hasFocus)
	}

	override fun setupView() {
		setupToolbar()
		vaultListPresenter.prepareView()
		activityRootView.setOnFilteredTouchEventForSecurityListener(object : Listener {
			override fun onFilteredTouchEventForSecurity() {
				vaultListPresenter.onFilteredTouchEventForSecurity()
			}
		})

		if (stopEditFilePressed() && sharedPreferencesHandler.keepUnlockedWhileEditing()) {
			hideNotification()
			unSuspendLock()
		}
	}

	private fun stopEditFilePressed(): Boolean {
		return vaultListIntent.stopEditFileNotification() != null && vaultListIntent.stopEditFileNotification()
	}

	private fun hideNotification() {
		OpenWritableFileNotification(context(), Uri.EMPTY).hide()
	}

	private fun unSuspendLock() {
		val cryptomatorApp = activity().application as CryptomatorApp
		cryptomatorApp.unSuspendLock()
	}

	override fun createFragment(): Fragment = VaultListFragment()

	override fun snackbarView(): View = vaultListFragment().rootView()

	override fun getCustomMenuResource(): Int = R.menu.menu_vault_list

	override fun onMenuItemSelected(itemId: Int): Boolean = when (itemId) {
		R.id.action_settings -> {
			vaultListPresenter.startIntent(settingsIntent())
			true
		}
		else -> super.onMenuItemSelected(itemId)
	}

	override fun isVaultLocked(vaultModel: VaultModel): Boolean {
		return vaultListFragment().isVaultLocked(vaultModel)
	}

	private fun setupToolbar() {
		toolbar.title = getString(R.string.app_name).toUpperCase(Locale.getDefault())
		setSupportActionBar(toolbar)
	}

	override fun showAddVaultBottomSheet() {
		showDialog(AddVaultBottomSheet())
	}

	override fun showRenameDialog(vaultModel: VaultModel) {
		showDialog(VaultRenameDialog.newInstance(vaultModel))
	}

	override fun rowMoved(fromPosition: Int, toPosition: Int) {
		vaultListFragment().rowMoved(fromPosition, toPosition)
	}

	override fun vaultMoved(vaults: List<VaultModel>) {
		vaultListFragment().vaultMoved(vaults)
	}

	override fun showVaultSettingsDialog(vaultModel: VaultModel) {
		val vaultSettingDialog = //
				SettingsVaultBottomSheet.newInstance(vaultModel)
		vaultSettingDialog.show(supportFragmentManager, "VaultSettings")
	}

	override fun renderVaultList(vaultModelCollection: List<VaultModel>) {
		vaultListFragment().showVaults(vaultModelCollection)
	}

	override fun showVaultCreationHint() {
		vaultListFragment().showVaultCreationHint()
	}

	override fun hideVaultCreationHint() {
		vaultListFragment().hideVaultCreationHint()
	}

	override fun deleteVaultFromAdapter(vaultId: Long) {
		vaultListFragment().deleteVaultFromAdapter(vaultId)
	}

	override fun addOrUpdateVault(vault: VaultModel) {
		vaultListFragment().addOrUpdateVault(vault)
	}

	override fun navigateToVaultContent(vault: VaultModel, decryptedRoot: CloudFolderModel) {
		vaultListPresenter.startIntent(browseFilesIntent().withTitle(vault.name).withFolder(decryptedRoot))
	}

	override fun renameVault(vaultModel: VaultModel) {
		vaultListFragment().addOrUpdateVault(vaultModel)
	}

	override fun onAddExistingVault() {
		vaultListPresenter.onAddExistingVault()
	}

	override fun onCreateVault() {
		vaultListPresenter.onCreateVault()
	}

	override fun onDeleteVaultClick(vaultModel: VaultModel) {
		VaultDeleteConfirmationDialog.newInstance(vaultModel) //
				.show(supportFragmentManager, "VaultDeleteConfirmationDialog")
	}

	override fun onRenameVaultClick(vaultModel: VaultModel) {
		vaultListPresenter.onRenameVaultClicked(vaultModel)
	}

	override fun onLockVaultClick(vaultModel: VaultModel) {
		vaultListPresenter.onVaultLockClicked(vaultModel)
	}

	override fun onChangePasswordClick(vaultModel: VaultModel) {
		vaultListPresenter.onChangePasswordClicked(vaultModel)
	}

	override fun onRenameClick(vaultModel: VaultModel, newVaultName: String) {
		vaultListPresenter.renameVault(vaultModel, newVaultName)
	}

	override fun onDeleteConfirmedClick(vaultModel: VaultModel) {
		vaultListPresenter.deleteVault(vaultModel)
	}

	override fun onAskForLockScreenFinished(setScreenLock: Boolean) {
		vaultListPresenter.onAskForLockScreenFinished(setScreenLock)
	}

	private fun vaultListFragment(): VaultListFragment = //
			getCurrentFragment(R.id.fragmentContainer) as VaultListFragment

	override fun onDeleteMissingVaultClicked(vault: Vault) {
		vaultListPresenter.onDeleteMissingVaultClicked(vault)
	}

	override fun onUpdateAppDialogLoaded() {
		showProgress(ProgressModel.GENERIC)
	}

	override fun installUpdate() {
		vaultListPresenter.installUpdate()
	}

	override fun cancelUpdateClicked() {
		closeDialog()
	}

	override fun showUpdateWebsite() {
		val url = "https://cryptomator.org/de/android/"
		val intent = Intent(Intent.ACTION_VIEW)
		intent.data = Uri.parse(url)
		startActivity(intent)
	}

	override fun onAskForBetaConfirmationFinished() {
		sharedPreferencesHandler.setBetaScreenDialogAlreadyShown()
	}
}
