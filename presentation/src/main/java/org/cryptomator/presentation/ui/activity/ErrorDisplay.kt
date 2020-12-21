package org.cryptomator.presentation.ui.activity

interface ErrorDisplay {

	fun showError(messageId: Int)

	fun showError(message: String)

}
