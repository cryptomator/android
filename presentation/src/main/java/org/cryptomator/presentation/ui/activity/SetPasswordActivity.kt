package org.cryptomator.presentation.ui.activity

import androidx.fragment.app.Fragment
import org.cryptomator.generator.Activity
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.ActivityLayoutBinding
import org.cryptomator.presentation.presenter.SetPasswordPresenter
import org.cryptomator.presentation.ui.activity.view.SetPasswordView
import org.cryptomator.presentation.ui.fragment.SetPasswordFragment
import javax.inject.Inject

@Activity
class SetPasswordActivity : BaseActivity<ActivityLayoutBinding>(ActivityLayoutBinding::inflate), SetPasswordView {

	@Inject
	lateinit var setPasswordPresenter: SetPasswordPresenter

	override fun setupView() {
		binding.mtToolbar.toolbar.setTitle(R.string.screen_set_password_title)
		setSupportActionBar(binding.mtToolbar.toolbar)
	}

	override fun createFragment(): Fragment = SetPasswordFragment()
}
