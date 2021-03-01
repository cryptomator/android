package org.cryptomator.presentation.presenter

import org.cryptomator.domain.di.PerView
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.intent.Intents
import org.cryptomator.presentation.ui.activity.view.SplashView
import javax.inject.Inject

@PerView
class SplashPresenter @Inject constructor(exceptionMappings: ExceptionHandlers) : Presenter<SplashView>(exceptionMappings) {

	override fun resumed() {
		Intents.vaultListIntent().startActivity(this)
		finish()
	}
}
