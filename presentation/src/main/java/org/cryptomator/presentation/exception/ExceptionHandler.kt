package org.cryptomator.presentation.exception

import org.cryptomator.presentation.ui.activity.view.View
import timber.log.Timber

abstract class ExceptionHandler {

	protected abstract fun supports(e: Throwable): Boolean
	protected abstract fun doHandle(view: View, e: Throwable)

	fun handle(view: View, e: Throwable): Boolean {
		return if (supports(e)) {
			log(e)
			doHandle(view, e)
			true
		} else {
			false
		}
	}

	protected open fun log(e: Throwable) {
		Timber.tag("ExceptionHandler").d(e)
	}
}
