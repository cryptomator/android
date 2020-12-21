package org.cryptomator.presentation.ui.activity.view

import org.cryptomator.domain.WebDavCloud
import org.cryptomator.presentation.intent.AuthenticateCloudIntent
import java.security.cert.X509Certificate

interface AuthenticateCloudView : View {

	fun intent(): AuthenticateCloudIntent
	fun skipTransition()
	fun showUntrustedCertificateDialog(cloud: WebDavCloud, certificate: X509Certificate)

}
