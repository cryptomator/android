package org.cryptomator.presentation.exception

import android.system.ErrnoException
import android.system.OsConstants
import org.cryptomator.presentation.R
import org.cryptomator.presentation.ui.activity.view.View
import org.cryptomator.util.ExceptionUtil

class NoSpaceLeftExceptionHandler : ExceptionHandler() {

	override fun supports(e: Throwable): Boolean {
		return ExceptionUtil.contains(e, ErrnoException::class.java) { it.errno == OsConstants.ENOSPC }
	}

	override fun doHandle(view: View, e: Throwable) {
		view.showError(R.string.error_no_space_left)
	}
}
