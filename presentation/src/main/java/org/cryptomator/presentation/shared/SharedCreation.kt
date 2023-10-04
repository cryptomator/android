package org.cryptomator.presentation.shared

import android.os.Looper
import org.cryptomator.presentation.BuildConfig
import org.cryptomator.presentation.CryptomatorApp
import org.cryptomator.presentation.logging.CrashLogging.Companion.setup
import org.cryptomator.presentation.logging.DebugLogger
import org.cryptomator.presentation.logging.ReleaseLogger
import timber.log.Timber

object SharedCreation {

	@Volatile
	private var hasRun = false

	@Synchronized
	fun onCreate() {
		if (hasRun) return
		hasRun = true

		require(Looper.getMainLooper().isCurrentThread)
		require(CryptomatorApp.isApplicationContextInitialized())

		setupLogging()
	}

	private fun setupLogging() {
		setupLoggingFramework()
		setup()
	}

	private fun setupLoggingFramework() {
		if (BuildConfig.DEBUG) {
			Timber.plant(DebugLogger())
		}
		//TODO Verify: Is the context already fully ready at this stage, when called from DocumentsProvider?
		Timber.plant(ReleaseLogger(CryptomatorApp.applicationContext()))
	}
}