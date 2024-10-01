package org.cryptomator.presentation.ui.activity

import org.cryptomator.generator.Activity
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.ActivityCryptomatorVariantsBinding
import org.cryptomator.presentation.presenter.CryptomatorVariantsPresenter
import org.cryptomator.presentation.ui.activity.view.CryptomatorVariantsView
import javax.inject.Inject

@Activity
class CryptomatorVariantsActivity : BaseActivity<ActivityCryptomatorVariantsBinding>(ActivityCryptomatorVariantsBinding::inflate), CryptomatorVariantsView {

	@Inject
	lateinit var presenter: CryptomatorVariantsPresenter

	override fun setupView() {
		binding.mtToolbar.toolbar.setTitle(R.string.screen_cryptomator_variants_title)
		setSupportActionBar(binding.mtToolbar.toolbar)

		binding.tvLiteSupported.text = "WebDAV, S3, Local Storage"
		binding.tvLiteUnsupported.text = "Dropbox, Google Drive, OneDrive, pCloud"

		binding.tvFdroidCustomSupported.text = "Dropbox, OneDrive, pCloud, WebDAV, S3, Local Storage"
		binding.tvFdroidCustomUnsupported.text = "Google Drive"

		binding.tvWebsiteSupported.text = "Dropbox, Google Drive, OneDrive, pCloud, WebDAV, S3, Local Storage"

		binding.btnInstallLiteVariant.setOnClickListener {
			presenter.onInstallMainFDroidVariantClicked()
		}
		binding.btnAddRepo.setOnClickListener {
			presenter.onAddRepoClicked()
		}
		binding.btnInstallFdroidVariant.setOnClickListener {
			presenter.onInstallFDroidVariantClicked()
		}
		binding.btnInstallWebsiteVariant.setOnClickListener {
			presenter.onInstallWebsiteVariantClicked()
		}
	}

}
