package org.cryptomator.presentation.ui.activity

import android.content.ClipData
import android.content.Intent
import android.content.Intent.ACTION_SEND
import android.content.Intent.ACTION_SEND_MULTIPLE
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import org.cryptomator.generator.Activity
import org.cryptomator.presentation.R
import org.cryptomator.presentation.model.CloudFolderModel
import org.cryptomator.presentation.model.ProgressModel.Companion.COMPLETED
import org.cryptomator.presentation.model.SharedFileModel
import org.cryptomator.presentation.model.VaultModel
import org.cryptomator.presentation.presenter.SharedFilesPresenter
import org.cryptomator.presentation.ui.activity.view.SharedFilesView
import org.cryptomator.presentation.ui.dialog.BiometricAuthKeyInvalidatedDialog
import org.cryptomator.presentation.ui.dialog.EnterPasswordDialog
import org.cryptomator.presentation.ui.dialog.NotEnoughVaultsDialog
import org.cryptomator.presentation.ui.dialog.ReplaceDialog
import org.cryptomator.presentation.ui.dialog.UploadCloudFileDialog
import org.cryptomator.presentation.ui.fragment.SharedFilesFragment
import org.cryptomator.presentation.util.BiometricAuthentication
import java.lang.String.format
import java.util.ArrayList
import javax.inject.Inject
import kotlinx.android.synthetic.main.toolbar_layout.toolbar
import timber.log.Timber

@Activity
class SharedFilesActivity : BaseActivity(), //
		SharedFilesView, //
		EnterPasswordDialog.Callback, //
		BiometricAuthentication.Callback, //
		ReplaceDialog.Callback, //
		NotEnoughVaultsDialog.Callback, //
		UploadCloudFileDialog.Callback {

	@Inject
	lateinit var presenter: SharedFilesPresenter

	private var contentName: String? = null

	override fun setupView() {
		handleIncomingContent(intent)
		setupToolbar()
	}

	private fun handleIncomingContent(content: Intent) {
		when (content.action) {
			ACTION_SEND // fall through
				, ACTION_SEND_MULTIPLE -> {
				Timber.tag("Sharing").d("Received intent")
				val clipData = content.clipData
				if (clipData != null) {
					handleIncomingClipData(clipData)
				}
			}
		}
	}

	private fun handleIncomingClipData(clipData: ClipData) {
		Timber.tag("Sharing").d("Received %d ClipData.Items", clipData.itemCount)
		if (clipData.itemCount == 1) {
			handleIncomingClipDataWithSingleItem(clipData.getItemAt(0))
		} else {
			handleIncomingClipDataWithMultipleItems(clipData)
		}
	}

	private fun handleIncomingClipDataWithSingleItem(item: ClipData.Item) {
		if (item.text != null && item.uri == null) {
			contentName = getString(R.string.screen_share_files_content_text)
			handleSendText(item.text.toString())
		} else if (item.uri != null) {
			contentName = getString(R.string.screen_share_files_content_file)
			handleSendFile(item.uri)
		}
	}

	private fun handleIncomingClipDataWithMultipleItems(clipData: ClipData) {
		val sharedFileUris = sharedFileUrisFrom(clipData)
		Timber.tag("Sharing").d("%d uris extracted", sharedFileUris.size)
		when {
			sharedFileUris.size == 1 -> {
				contentName = getString(R.string.screen_share_files_content_file)
				handleSendFile(sharedFileUris[0])
			}
			sharedFileUris.size > 1 -> {
				contentName = getString(R.string.screen_share_files_content_files)
				handleSendMultipleFiles(sharedFileUris)
			}
		}
	}

	private fun sharedFileUrisFrom(clipData: ClipData): List<Uri> {
		val uriList = ArrayList<Uri>(clipData.itemCount)
		(0 until clipData.itemCount).forEach { i ->
			clipData.getItemAt(i).uri
					?.let { uriList.add(it) }
					?: Timber.tag("Sharing").i("Item %d without uri", i)
		}
		return uriList
	}

	private fun handleSendMultipleFiles(uriList: List<Uri>) {
		presenter.onFilesShared(uriList)
	}

	private fun handleSendFile(fileUri: Uri) {
		presenter.onFileShared(fileUri)
	}

	private fun handleSendText(text: String) {
		presenter.onTextShared(text)
	}

	private fun setupToolbar() {
		toolbar.title = format(getString(R.string.screen_share_files_title), contentName)
		setSupportActionBar(toolbar)
		supportActionBar?.let {
			it.setDisplayHomeAsUpEnabled(true)
			it.setHomeAsUpIndicator(R.drawable.ic_clear)
		}
	}

	override fun createFragment(): Fragment? = SharedFilesFragment()

	public override fun onMenuItemSelected(itemId: Int): Boolean = when (itemId) {
		android.R.id.home -> {
			finish()
			true
		}
		else -> super.onMenuItemSelected(itemId)
	}

	override fun displayVaults(vaults: List<VaultModel>) {
		sharedFilesFragment().displayVaults(vaults)
	}

	override fun displayFilesToUpload(sharedFiles: List<SharedFileModel>) {
		sharedFilesFragment().displayFilesToUpload(sharedFiles)
	}

	override fun displayDialogUnableToUploadFiles() {
		//UnableToShareFilesDialog.withContext(this).show()
	}

	private fun sharedFilesFragment(): SharedFilesFragment = getCurrentFragment(R.id.fragmentContainer) as SharedFilesFragment

	@RequiresApi(api = Build.VERSION_CODES.M)
	override fun showEnterPasswordDialog(vault: VaultModel) {
		if (vaultWithBiometricAuthEnabled(vault)) {
			BiometricAuthentication(this, context(), BiometricAuthentication.CryptoMode.DECRYPT, presenter.useConfirmationInFaceUnlockBiometricAuthentication())
					.startListening(sharedFilesFragment(), vault)
		} else {
			showDialog(EnterPasswordDialog.newInstance(vault))
		}
	}

	override fun showReplaceDialog(existingFiles: List<String>, size: Int) {
		ReplaceDialog.withContext(this).show(existingFiles, size)
	}

	override fun showChosenLocation(folder: CloudFolderModel) {
		sharedFilesFragment().showChosenLocation(folder)
	}

	override fun showBiometricAuthKeyInvalidatedDialog() {
		showDialog(BiometricAuthKeyInvalidatedDialog.newInstance())
	}

	override fun showUploadDialog(uploadingFiles: Int) {
		showDialog(UploadCloudFileDialog.newInstance(uploadingFiles))
	}

	override fun onUnlockClick(vaultModel: VaultModel, password: String) {
		presenter.onUnlockPressed(vaultModel, password)
	}

	override fun onUnlockCanceled() {
		presenter.onUnlockCanceled()
	}

	override fun onReplacePositiveClicked() {
		presenter.onReplaceExistingFilesPressed()
	}

	override fun onReplaceNegativeClicked() {
		presenter.onSkipExistingFilesPressed()
	}

	override fun onReplaceCanceled() {
		showProgress(COMPLETED)
	}

	override fun onNotEnoughVaultsOkClicked() {
		finish()
	}

	override fun onNotEnoughVaultsCreateVaultClicked() {
		packageManager.getLaunchIntentForPackage(packageName)
				?.let {
					it.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
					startActivity(it)
				}
		finish()
	}

	override fun onBiometricAuthenticated(vault: VaultModel) {
		presenter.onUnlockPressed(vault, vault.password)
	}

	override fun onBiometricAuthenticationFailed(vault: VaultModel) {
		showDialog(EnterPasswordDialog.newInstance(vault))
	}

	override fun onBiometricKeyInvalidated(vault: VaultModel) {
		presenter.onBiometricAuthKeyInvalidated()
	}

	private fun vaultWithBiometricAuthEnabled(vault: VaultModel): Boolean = vault.password != null

	override fun onUploadCanceled() {
		presenter.onUploadCanceled()
	}
}
