package org.cryptomator.presentation.logging

import timber.log.Timber

class CrashLogging private constructor(private val systemUncaughtExceptionHandler: Thread.UncaughtExceptionHandler?) : Thread.UncaughtExceptionHandler {

	override fun uncaughtException(t: Thread, e: Throwable) {
		Timber.tag("CrashLogging").e(e)
		systemUncaughtExceptionHandler?.uncaughtException(t, e)
	}

	companion object {

		fun setup() {
			Thread.setDefaultUncaughtExceptionHandler(CrashLogging(Thread.getDefaultUncaughtExceptionHandler()))
		}
	}
}
