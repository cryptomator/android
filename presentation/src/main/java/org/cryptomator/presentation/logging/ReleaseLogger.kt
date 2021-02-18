package org.cryptomator.presentation.logging

import android.content.Context
import android.util.Log
import org.cryptomator.util.SharedPreferencesHandler
import timber.log.Timber

class ReleaseLogger(context: Context) : Timber.Tree() {

	private val priorityNames = charArrayOf( //
			'?',  //
			'?',  //
			'V',  //
			'D',  //
			'I',  //
			'W',  //
			'E',  //
			'A' //
	)
	private val log: LogRotator

	override fun isLoggable(tag: String?, priority: Int): Boolean {
		return debugMode || priority >= LOG_LEVEL_WHEN_DEBUG_IS_DISABLED
	}

	override fun log(priority: Int, tag: String?, message: String, throwable: Throwable?) {
		val line = StringBuilder()
		line //
				.append(priorityNames[validPriority(priority)]).append('\t') //
				.append(FormattedTime.now()).append('\t') //
				.append(tag ?: "App").append('\t') //
				.append(message)
		if (throwable != null) {
			line.append("\nErrorCode: ").append(GeneratedErrorCode.of(throwable))
		}
		log.log(line.toString())
	}

	private fun validPriority(priority: Int): Int {
		return if (priority in 1..7) {
			priority
		} else 0
	}

	companion object {

		private const val LOG_LEVEL_WHEN_DEBUG_IS_DISABLED = Log.INFO

		@Volatile
		private var debugMode: Boolean = false

		fun updateDebugMode(debugMode: Boolean) {
			Companion.debugMode = debugMode
			Timber.tag("Logging").i(if (debugMode) "Debug mode enabled" else "Debug mode disabled")
		}
	}

	init {
		debugMode = SharedPreferencesHandler(context).debugMode()
		log = LogRotator(context)
	}
}
