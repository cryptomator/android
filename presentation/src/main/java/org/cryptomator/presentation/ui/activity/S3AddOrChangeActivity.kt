package org.cryptomator.presentation.ui.activity

import androidx.fragment.app.Fragment
import org.cryptomator.generator.Activity
import org.cryptomator.generator.InjectIntent
import org.cryptomator.presentation.R
import org.cryptomator.presentation.intent.S3AddOrChangeIntent
import org.cryptomator.presentation.presenter.S3AddOrChangePresenter
import org.cryptomator.presentation.ui.activity.view.S3AddOrChangeView
import org.cryptomator.presentation.ui.fragment.S3AddOrChangeFragment
import javax.inject.Inject
import kotlinx.android.synthetic.main.toolbar_layout.toolbar

@Activity
class S3AddOrChangeActivity : BaseActivity(), S3AddOrChangeView {

	@Inject
	lateinit var s3AddOrChangePresenter: S3AddOrChangePresenter

	@InjectIntent
	lateinit var s3AddOrChangeIntent: S3AddOrChangeIntent

	override fun setupView() {
		toolbar.setTitle(R.string.screen_s3_settings_title)
		setSupportActionBar(toolbar)
	}

	override fun createFragment(): Fragment = S3AddOrChangeFragment.newInstance(s3AddOrChangeIntent.s3Cloud())

	override fun onCheckUserInputSucceeded(accessKey: String, secretKey: String, bucket: String, endpoint: String?, region: String?, cloudId: Long?) {
		s3AddOrChangeFragment().hideKeyboard()
		s3AddOrChangePresenter.authenticate(accessKey, secretKey, bucket, endpoint, region, cloudId)
	}

	private fun s3AddOrChangeFragment(): S3AddOrChangeFragment = getCurrentFragment(R.id.fragmentContainer) as S3AddOrChangeFragment

}
