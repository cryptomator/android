package org.cryptomator.presentation.logging

import timber.log.Timber.DebugTree

class DebugLogger : DebugTree() {
	override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
		val loggingMessage = if (t != null) {
			"""
			$message
			ErrorCode: ${GeneratedErrorCode.of(t)}
			""".trimIndent()
		} else {
			message
		}

		super.log(priority, tag, loggingMessage, t)
	}
}
