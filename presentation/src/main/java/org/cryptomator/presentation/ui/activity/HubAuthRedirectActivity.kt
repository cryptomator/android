package org.cryptomator.presentation.ui.activity

import android.os.Bundle
import org.cryptomator.generator.Activity
import org.cryptomator.presentation.databinding.ActivityEmptyBinding
import org.cryptomator.presentation.presenter.HubAuthRedirectPresenter
import org.cryptomator.presentation.ui.activity.view.HubAuthRedirectView
import javax.inject.Inject

/**
 * Receives the Hub login's redirect in place of AppAuth's receiver, which can't reach an unlock running in another
 * app's task, e.g. one started from the system file manager or a share.
 */
@Activity
class HubAuthRedirectActivity : BaseActivity<ActivityEmptyBinding>(ActivityEmptyBinding::inflate), HubAuthRedirectView {

	@Inject
	lateinit var presenter: HubAuthRedirectPresenter

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		presenter.onRedirect(intent.data)
	}
}
