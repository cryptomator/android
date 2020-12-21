package org.cryptomator.presentation.exception

import android.content.Context
import org.cryptomator.domain.di.PerView
import org.cryptomator.presentation.R
import org.cryptomator.presentation.ui.activity.view.View
import timber.log.Timber
import javax.inject.Inject

@PerView
class DefaultExceptionHandler @Inject constructor(context: Context) : ExceptionHandler() {

	private val defaultMessage: String = context.getString(R.string.error_generic)

	override fun supports(e: Throwable): Boolean {
		return true
	}

	override fun log(e: Throwable) {
		Timber.tag("ExceptionHandler").e(e)
	}

	override fun doHandle(view: View, e: Throwable) {
		view.showError(defaultMessage)
	}

}
