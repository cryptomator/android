package org.cryptomator.presentation.exception

import org.cryptomator.domain.exception.CancellationException
import org.cryptomator.presentation.ui.activity.view.View
import org.cryptomator.util.ExceptionUtil
import timber.log.Timber

class CancellationExceptionHandler : ExceptionHandler() {

	public override fun supports(e: Throwable): Boolean {
		return ExceptionUtil.contains(e, CancellationException::class.java)
	}

	override fun log(e: Throwable) {
		Timber.tag("ExceptionHandler").v(e, "Ignored CancellationException")
	}

	override fun doHandle(view: View, e: Throwable) {
		// completely ignore
	}
}
