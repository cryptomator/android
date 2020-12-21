package org.cryptomator.presentation.exception

import org.cryptomator.domain.exception.MissingCryptorException
import org.cryptomator.presentation.R
import org.cryptomator.presentation.intent.Intents
import org.cryptomator.presentation.ui.activity.view.View
import org.cryptomator.util.ExceptionUtil

class MissingCryptorExceptionHandler : ExceptionHandler() {

	public override fun supports(e: Throwable): Boolean {
		return ExceptionUtil.contains(e, MissingCryptorException::class.java)
	}

	public override fun doHandle(view: View, e: Throwable) {
		view.showMessage(R.string.error_vault_has_been_locked)
		Intents.vaultListIntent() //
				.preventGoingBackInHistory() //
				.startActivity(view)
	}
}
