package org.cryptomator.presentation.ui.activity

import org.cryptomator.generator.Activity
import org.cryptomator.presentation.R
import org.cryptomator.presentation.presenter.CryptomatorVariantsPresenter
import org.cryptomator.presentation.ui.activity.view.CryptomatorVariantsView
import javax.inject.Inject
import kotlinx.android.synthetic.main.activity_cryptomator_variants.btnAddRepo
import kotlinx.android.synthetic.main.activity_cryptomator_variants.btnInstallFDroidVariant
import kotlinx.android.synthetic.main.activity_cryptomator_variants.btnInstallLiteVariant
import kotlinx.android.synthetic.main.activity_cryptomator_variants.btnInstallWebsiteVariant
import kotlinx.android.synthetic.main.activity_cryptomator_variants.tvFdroidCustomSupported
import kotlinx.android.synthetic.main.activity_cryptomator_variants.tvFdroidCustomUnsupported
import kotlinx.android.synthetic.main.activity_cryptomator_variants.tvLiteSupported
import kotlinx.android.synthetic.main.activity_cryptomator_variants.tvLiteUnsupported
import kotlinx.android.synthetic.main.activity_cryptomator_variants.tvWebsiteSupported
import kotlinx.android.synthetic.main.toolbar_layout.toolbar

@Activity(layout = R.layout.activity_cryptomator_variants)
class CryptomatorVariantsActivity : BaseActivity(), CryptomatorVariantsView {

	@Inject
	lateinit var presenter: CryptomatorVariantsPresenter

	override fun setupView() {
		toolbar.title = getString(R.string.screen_cryptomator_variants_title)
		setSupportActionBar(toolbar)

		tvLiteSupported.text = "WebDAV, S3, Local Storage"
		tvLiteUnsupported.text = "Dropbox, Google Drive, OneDrive, pCloud"

		tvFdroidCustomSupported.text = "Dropbox, OneDrive, pCloud, WebDAV, S3, Local Storage"
		tvFdroidCustomUnsupported.text = "Google Drive"

		tvWebsiteSupported.text = "Dropbox, Google Drive, OneDrive, pCloud, WebDAV, S3, Local Storage"

		btnInstallLiteVariant.setOnClickListener {
			presenter.onInstallMainFDroidVariantClicked()
		}
		btnAddRepo.setOnClickListener {
			presenter.onAddRepoClicked()
		}
		btnInstallFDroidVariant.setOnClickListener {
			presenter.onInstallFDroidVariantClicked()
		}
		btnInstallWebsiteVariant.setOnClickListener {
			presenter.onInstallWebsiteVariantClicked()
		}
	}

}
