package org.cryptomator.presentation.ui.activity

import androidx.fragment.app.Fragment
import org.cryptomator.generator.Activity
import org.cryptomator.generator.InjectIntent
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.ActivityLayoutBinding
import org.cryptomator.presentation.intent.SmbAddOrChangeIntent
import org.cryptomator.presentation.presenter.SmbAddOrChangePresenter
import org.cryptomator.presentation.ui.activity.view.SmbAddOrChangeView
import org.cryptomator.presentation.ui.fragment.SmbAddOrChangeFragment
import javax.inject.Inject

@Activity
class SmbAddOrChangeActivity : BaseActivity<ActivityLayoutBinding>(ActivityLayoutBinding::inflate), SmbAddOrChangeView {

	@Inject
	lateinit var smbAddOrChangePresenter: SmbAddOrChangePresenter

	@InjectIntent
	lateinit var smbAddOrChangeIntent: SmbAddOrChangeIntent

	override fun setupView() {
		binding.mtToolbar.toolbar.setTitle(R.string.screen_smb_settings_title)
		setSupportActionBar(binding.mtToolbar.toolbar)
	}

	override fun createFragment(): Fragment = SmbAddOrChangeFragment.newInstance(smbAddOrChangeIntent.smbCloud())

	override fun onCheckUserInputSucceeded(url: String, username: String, password: String, cloudId: Long?) {
		smbAddOrChangeFragment().hideKeyboard()
		smbAddOrChangePresenter.authenticate(url, username, password, cloudId)
	}

	private fun smbAddOrChangeFragment(): SmbAddOrChangeFragment = getCurrentFragment(R.id.fragment_container) as SmbAddOrChangeFragment

}
