package org.cryptomator.presentation.ui.activity

import androidx.fragment.app.Fragment
import org.cryptomator.domain.S3Provider
import org.cryptomator.generator.Activity
import org.cryptomator.generator.InjectIntent
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.ActivityLayoutBinding
import org.cryptomator.presentation.intent.S3AddOrChangeIntent
import org.cryptomator.presentation.model.CloudTypeModel
import org.cryptomator.presentation.presenter.S3AddOrChangePresenter
import org.cryptomator.presentation.ui.activity.view.S3AddOrChangeView
import org.cryptomator.presentation.ui.fragment.S3AddOrChangeFragment
import javax.inject.Inject

@Activity
class S3AddOrChangeActivity : BaseActivity<ActivityLayoutBinding>(ActivityLayoutBinding::inflate), S3AddOrChangeView {

	@Inject
	lateinit var s3AddOrChangePresenter: S3AddOrChangePresenter

	@InjectIntent
	lateinit var s3AddOrChangeIntent: S3AddOrChangeIntent

	private val s3Provider: S3Provider?
		get() = s3AddOrChangeIntent.s3Cloud()?.provider() ?: s3AddOrChangeIntent.s3Provider()

	override fun setupView() {
		binding.mtToolbar.toolbar.setTitle(s3Provider?.let { CloudTypeModel.valueOf(it).displayNameResource } ?: R.string.screen_s3_settings_title)
		setSupportActionBar(binding.mtToolbar.toolbar)
	}

	override fun setupPresenter() {
		s3AddOrChangePresenter.setS3Provider(s3Provider)
	}

	override fun createFragment(): Fragment = S3AddOrChangeFragment.newInstance(s3AddOrChangeIntent.s3Cloud(), s3Provider)

	override fun onCheckUserInputSucceeded(accessKey: String, secretKey: String, bucket: String, endpoint: String?, region: String?, cloudId: Long?, displayName: String) {
		s3AddOrChangeFragment().hideKeyboard()
		s3AddOrChangePresenter.authenticate(accessKey, secretKey, bucket, endpoint, region, cloudId, displayName)
	}

	private fun s3AddOrChangeFragment(): S3AddOrChangeFragment = getCurrentFragment(R.id.fragment_container) as S3AddOrChangeFragment

}
