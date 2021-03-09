package org.cryptomator.presentation.ui.activity

import androidx.fragment.app.Fragment
import org.cryptomator.generator.Activity
import org.cryptomator.presentation.R
import org.cryptomator.presentation.model.CloudFolderModel
import org.cryptomator.presentation.model.VaultModel
import org.cryptomator.presentation.presenter.AutoUploadChooseVaultPresenter
import org.cryptomator.presentation.ui.activity.view.AutoUploadChooseVaultView
import org.cryptomator.presentation.ui.dialog.NotEnoughVaultsDialog
import org.cryptomator.presentation.ui.fragment.AutoUploadChooseVaultFragment
import javax.inject.Inject
import kotlinx.android.synthetic.main.toolbar_layout.toolbar

@Activity
class AutoUploadChooseVaultActivity : BaseActivity(), //
		AutoUploadChooseVaultView, //
		NotEnoughVaultsDialog.Callback {

	@Inject
	lateinit var presenter: AutoUploadChooseVaultPresenter

	override fun setupView() {
		setupToolbar()
	}

	private fun setupToolbar() {
		toolbar.title = getString(R.string.screen_settings_auto_photo_upload_title)
		setSupportActionBar(toolbar)
		supportActionBar?.let {
			it.setDisplayHomeAsUpEnabled(true)
			it.setHomeAsUpIndicator(R.drawable.ic_clear)
		}
	}

	override fun createFragment(): Fragment = AutoUploadChooseVaultFragment()


	override fun displayVaults(vaults: List<VaultModel>) {
		autoUploadChooseVaultFragment().displayVaults(vaults)
	}

	override fun displayDialogUnableToUploadFiles() {
		NotEnoughVaultsDialog //
				.withContext(this) //
				.andTitle(getString(R.string.dialog_unable_to_auto_upload_files_title)) //
				.show()
	}

	override fun onNotEnoughVaultsOkClicked() {
		finish()
	}

	override fun onNotEnoughVaultsCreateVaultClicked() {
		// FIXME #202: vault list activity is twice on the stack
		val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
		launchIntent?.let { startActivity(it) }
		finish()
	}

	override fun showChosenLocation(location: CloudFolderModel) {
		autoUploadChooseVaultFragment().showChosenLocation(location)
	}

	private fun autoUploadChooseVaultFragment(): AutoUploadChooseVaultFragment = getCurrentFragment(R.id.fragmentContainer) as AutoUploadChooseVaultFragment
}
