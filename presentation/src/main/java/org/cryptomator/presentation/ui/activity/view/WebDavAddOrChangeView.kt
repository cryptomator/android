package org.cryptomator.presentation.ui.activity.view

interface WebDavAddOrChangeView : View {

	fun onCheckUserInputSucceeded(urlPort: String, username: String, password: String, cloudId: Long?, certificate: String?)

	fun showAskForHttpDialog(urlPort: String, username: String, password: String, cloudId: Long?, certificate: String?)

}
