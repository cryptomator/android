package org.cryptomator.presentation.exception

import org.cryptomator.presentation.ui.activity.view.View

internal abstract class MessageExceptionHandler<T : Throwable>(private val type: Class<T>) : ExceptionHandler() {

	override fun supports(e: Throwable): Boolean {
		return type.isInstance(e)
	}

	override fun doHandle(view: View, e: Throwable) {
		view.showError(toMessage(type.cast(e)))
	}

	abstract fun toMessage(e: T?): String

}
