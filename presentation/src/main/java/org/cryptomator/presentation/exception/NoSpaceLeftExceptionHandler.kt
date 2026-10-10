package org.cryptomator.presentation.exception

import android.system.ErrnoException
import android.system.OsConstants
import org.cryptomator.presentation.R
import org.cryptomator.presentation.ui.activity.view.View
import org.cryptomator.util.ExceptionUtil

/**
 * Shows a dedicated error when an operation fails because the device ran out of storage, instead of
 * surfacing the wrapping [org.cryptomator.domain.exception.FatalBackendException] as a generic error.
 */
class NoSpaceLeftExceptionHandler : ExceptionHandler() {

	/**
	 * Returns `true` if the cause chain of [e] contains an [ErrnoException] with errno
	 * [OsConstants.ENOSPC] (no space left on device).
	 */
	override fun supports(e: Throwable): Boolean {
		return ExceptionUtil.contains(e, ErrnoException::class.java) { it.errno == OsConstants.ENOSPC }
	}

	/** Displays the "not enough storage space" message to the user. */
	override fun doHandle(view: View, e: Throwable) {
		view.showError(R.string.error_no_space_left)
	}
}
