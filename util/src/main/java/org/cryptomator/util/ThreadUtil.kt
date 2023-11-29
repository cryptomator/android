package org.cryptomator.util

import android.os.Looper
import timber.log.Timber

object ThreadUtil {

	val isMainThread: Boolean
		get() = Looper.getMainLooper().isCurrentThread

	fun assertNotMainThread() {
		check(!isMainThread) { "Error: Currently executing on main thread; aborting" }
	}

	fun assumeNotMainThread() {
		if (isMainThread) {
			Timber.tag("ThreadUtil").w(Exception(), "Warning: Currently executing on main thread")
		}
	}
}