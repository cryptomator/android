package org.cryptomator.presentation.exception

import android.content.Context
import android.system.ErrnoException
import android.system.OsConstants
import org.cryptomator.domain.di.PerView
import org.cryptomator.presentation.R
import org.cryptomator.presentation.ui.activity.view.View
import org.cryptomator.util.ExceptionUtil
import javax.inject.Inject
import timber.log.Timber

@PerView
class DefaultExceptionHandler @Inject constructor(context: Context) : ExceptionHandler() {

	private val defaultMessage: String = context.getString(R.string.error_generic)
	private val deviceStorageMessage: String = context.getString(R.string.error_device_storage_full)

	override fun supports(e: Throwable): Boolean {
		return true
	}

	override fun log(e: Throwable) {
		Timber.tag("ExceptionHandler").e(e)
	}

	override fun doHandle(view: View, e: Throwable) {
		view.showError(if (isDeviceStorageExhausted(e)) deviceStorageMessage else defaultMessage)
	}

	private fun isDeviceStorageExhausted(e: Throwable): Boolean {
		return ExceptionUtil.contains(e, ErrnoException::class.java) { errnoException ->
			errnoException.errno == OsConstants.ENOSPC
		}
	}

}
