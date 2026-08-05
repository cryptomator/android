package org.cryptomator.presentation.ui.activity

import org.cryptomator.generator.Activity
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.ActivityCryptomatorVariantsBinding
import org.cryptomator.presentation.presenter.CryptomatorVariantsPresenter
import org.cryptomator.presentation.ui.activity.view.CryptomatorVariantsView
import org.cryptomator.presentation.ui.layout.applySystemBarsPadding
import javax.inject.Inject

@Activity
class CryptomatorVariantsActivity : BaseActivity<ActivityCryptomatorVariantsBinding>(ActivityCryptomatorVariantsBinding::inflate), CryptomatorVariantsView {

	@Inject
	lateinit var presenter: CryptomatorVariantsPresenter

	override fun setupView() {
		binding.mtToolbar.toolbar.setTitle(R.string.screen_cryptomator_variants_title)
		setSupportActionBar(binding.mtToolbar.toolbar)

		binding.tvLiteSupported.setText(R.string.screen_cryptomator_variants_lite_supported)
		binding.tvLiteUnsupported.setText(R.string.screen_cryptomator_variants_lite_unsupported)

		binding.tvFdroidCustomSupported.setText(R.string.screen_cryptomator_variants_fdroid_custom_supported)
		binding.tvFdroidCustomUnsupported.setText(R.string.screen_cryptomator_variants_fdroid_custom_unsupported)

		binding.tvWebsiteSupported.setText(R.string.screen_cryptomator_variants_website_supported)

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
		binding.tvHint.applySystemBarsPadding(bottom = true)
	}

}
