package org.cryptomator.presentation.ui.activity

import org.cryptomator.domain.WebDavCloud
import org.cryptomator.generator.Activity
import org.cryptomator.generator.InjectIntent
import org.cryptomator.presentation.R
import org.cryptomator.presentation.intent.AuthenticateCloudIntent
import org.cryptomator.presentation.presenter.AuthenticateCloudPresenter
import org.cryptomator.presentation.ui.activity.view.AuthenticateCloudView
import org.cryptomator.presentation.ui.dialog.AssignSslCertificateDialog

import java.security.cert.X509Certificate

import javax.inject.Inject

@Activity(layout = R.layout.activity_empty)
class AuthenticateCloudActivity : BaseActivity(),
		AuthenticateCloudView,
		AssignSslCertificateDialog.Callback {

	@Inject
	lateinit var presenter: AuthenticateCloudPresenter

	@InjectIntent
	lateinit var authenticateCloudIntent: AuthenticateCloudIntent

	override fun intent(): AuthenticateCloudIntent = authenticateCloudIntent

	override fun finish() {
		super.finish()
		skipTransition()
	}

	override fun skipTransition() {
		overridePendingTransition(0, 0)
	}

	override fun showUntrustedCertificateDialog(cloud: WebDavCloud, certificate: X509Certificate) {
		showDialog(AssignSslCertificateDialog.newInstance(cloud, certificate))
	}

	override fun onAcceptCertificateClicked(cloud: WebDavCloud, certificate: X509Certificate) {
		presenter.onAcceptWebDavCertificateClicked(cloud, certificate)
	}

	override fun onAcceptCertificateDenied() {
		presenter.onAcceptWebDavCertificateDenied()
	}
}
