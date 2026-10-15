package org.cryptomator.presentation.ui.activity.view

interface SmbAddOrChangeView : View {

	fun onCheckUserInputSucceeded(url: String, username: String, password: String, cloudId: Long?)

}
