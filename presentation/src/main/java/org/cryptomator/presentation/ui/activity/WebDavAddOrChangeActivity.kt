package org.cryptomator.presentation.ui.activity

import androidx.fragment.app.Fragment
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.generator.Activity
import org.cryptomator.generator.InjectIntent
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.ActivityLayoutBinding
import org.cryptomator.presentation.intent.WebDavAddOrChangeIntent
import org.cryptomator.presentation.presenter.WebDavAddOrChangePresenter
import org.cryptomator.presentation.ui.activity.view.WebDavAddOrChangeView
import org.cryptomator.presentation.ui.dialog.WebDavAskForHttpDialog
import org.cryptomator.presentation.ui.fragment.WebDavAddOrChangeFragment
import java.net.URI
import java.net.URISyntaxException
import javax.inject.Inject

@Activity
class WebDavAddOrChangeActivity : BaseActivity<ActivityLayoutBinding>(ActivityLayoutBinding::inflate),
	WebDavAddOrChangeView,
	WebDavAskForHttpDialog.Callback {

	@Inject
	lateinit var webDavAddOrChangePresenter: WebDavAddOrChangePresenter

	@InjectIntent
	lateinit var webDavAddOrChangeIntent: WebDavAddOrChangeIntent

	override fun setupView() {
		binding.mtToolbar.toolbar.setTitle(R.string.screen_webdav_settings_title)
		setSupportActionBar(binding.mtToolbar.toolbar)
	}

	override fun createFragment(): Fragment = WebDavAddOrChangeFragment.newInstance(webDavAddOrChangeIntent.webDavCloud())

	override fun onCheckUserInputSucceeded(urlPort: String, username: String, password: String, cloudId: Long?, certificate: String?) {
		webDavAddOrChangeFragment().hideKeyboard()
		webDavAddOrChangePresenter.authenticate(username, password, urlPort, cloudId, certificate)
	}

	override fun showAskForHttpDialog(urlPort: String, username: String, password: String, cloudId: Long?, certificate: String?) {
		try {
			showDialog(WebDavAskForHttpDialog.newInstance(URI(urlPort), username, password, cloudId, certificate))
		} catch (e: URISyntaxException) {
			throw FatalBackendException(e)
		}
	}

	override fun onAksForHttpFinished(username: String, password: String, url: String, cloudId: Long?, certificate: String?) {
		webDavAddOrChangePresenter.authenticate(username, password, url, cloudId, certificate)
	}

	private fun webDavAddOrChangeFragment(): WebDavAddOrChangeFragment = getCurrentFragment(R.id.fragment_container) as WebDavAddOrChangeFragment

}
