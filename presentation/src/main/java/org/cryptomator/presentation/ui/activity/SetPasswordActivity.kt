package org.cryptomator.presentation.ui.activity

import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.toolbar_layout.*
import org.cryptomator.generator.Activity
import org.cryptomator.presentation.R
import org.cryptomator.presentation.presenter.SetPasswordPresenter
import org.cryptomator.presentation.ui.activity.view.SetPasswordView
import org.cryptomator.presentation.ui.fragment.SetPasswordFragment
import javax.inject.Inject

@Activity
class SetPasswordActivity : BaseActivity(), SetPasswordView {

	@Inject
	lateinit var setPasswordPresenter: SetPasswordPresenter

	override fun setupView() {
		toolbar.setTitle(R.string.screen_set_password_title)
		setSupportActionBar(toolbar)
	}

	override fun createFragment(): Fragment = SetPasswordFragment()
}
