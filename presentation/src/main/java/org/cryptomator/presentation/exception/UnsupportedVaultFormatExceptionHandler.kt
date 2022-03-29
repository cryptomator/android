package org.cryptomator.presentation.exception

import org.cryptomator.cryptolib.api.UnsupportedVaultFormatException
import org.cryptomator.presentation.R
import org.cryptomator.presentation.ui.activity.view.View
import org.cryptomator.presentation.util.ResourceHelper.Companion.getString
import org.cryptomator.util.ExceptionUtil

class UnsupportedVaultFormatExceptionHandler : ExceptionHandler() {

	override fun supports(e: Throwable): Boolean {
		return ExceptionUtil.contains(e, UnsupportedVaultFormatException::class.java)
	}

	override fun doHandle(view: View, e: Throwable) {
		val detectedFormat = ExceptionUtil.extract(e, UnsupportedVaultFormatException::class.java).get().detectedVersion
		view.showError(String.format(getString(R.string.error_vault_version_not_supported), detectedFormat))
	}
}
