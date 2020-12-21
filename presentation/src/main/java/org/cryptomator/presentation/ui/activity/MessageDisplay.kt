package org.cryptomator.presentation.ui.activity

interface MessageDisplay {

	fun showMessage(messageId: Int, vararg args: Any)

	fun showMessage(message: String, vararg args: Any)

}
