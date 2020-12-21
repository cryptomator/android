package org.cryptomator.presentation.exception

import org.cryptomator.domain.exception.NoSuchVaultException
import org.cryptomator.presentation.ui.activity.view.View
import org.cryptomator.presentation.ui.dialog.VaultNotFoundDialog.Companion.withContext
import org.cryptomator.util.ExceptionUtil

class NoSuchVaultExceptionHandler : ExceptionHandler() {
	override fun supports(e: Throwable): Boolean {
		return ExceptionUtil.contains(e, NoSuchVaultException::class.java)
	}

	override fun doHandle(view: View, e: Throwable) {
		view.closeDialog()
		val vault = ExceptionUtil.extract(e, NoSuchVaultException::class.java).get().vault
		withContext(view.activity()).show(vault)
	}
}
