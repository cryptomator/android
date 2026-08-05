package org.cryptomator.presentation.ui.activity.view

interface SmbAddOrChangeView : View {

	fun onCheckUserInputSucceeded(urlPort: String, username: String, password: String, domain: String, cloudId: Long?)

}
