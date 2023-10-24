package org.cryptomator.presentation.ui.activity

import android.os.Bundle
import org.cryptomator.domain.exception.authentication.AuthenticationException
import org.cryptomator.generator.Activity
import org.cryptomator.presentation.R
import org.cryptomator.presentation.presenter.AutoUploadRefreshTokenPresenter
import org.cryptomator.presentation.ui.activity.view.AutoUploadRefreshTokenView
import javax.inject.Inject
import timber.log.Timber

@Activity(layout = R.layout.activity_empty)
class AutoUploadRefreshTokenActivity : BaseActivity(), AutoUploadRefreshTokenView {

	@Inject
	lateinit var presenter: AutoUploadRefreshTokenPresenter

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val cloud = intent.getSerializableExtra(AUTHENTICATION_EXCEPTION_ARG) as? AuthenticationException
		cloud?.let {
			presenter.refreshCloudToken(it)
		} ?: run {
			Timber.tag("AutoUploadRefreshTokenActivity").e("WrongCredentialsException not provided")
			finish()
		}
	}

	companion object {

		const val AUTHENTICATION_EXCEPTION_ARG = "authenticationException"
	}

}
