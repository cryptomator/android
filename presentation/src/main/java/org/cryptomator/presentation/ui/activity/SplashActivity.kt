package org.cryptomator.presentation.ui.activity

import org.cryptomator.generator.Activity
import org.cryptomator.presentation.presenter.SplashPresenter
import org.cryptomator.presentation.ui.activity.view.SplashView

import javax.inject.Inject

@Activity(secure = false)
class SplashActivity : BaseActivity(), SplashView {

	@Inject
	lateinit var splashPresenter: SplashPresenter
}
