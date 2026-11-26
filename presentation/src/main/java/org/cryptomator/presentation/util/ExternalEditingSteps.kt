package org.cryptomator.presentation.util

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Counts the running steps of opening a file in another app: pinning its base, making the copy a Microsoft app gets and
 * saving that copy afterwards. A step outlives the browser that started it, so the count is kept here, where a recreated
 * or reopened browser finds it.
 */
@Singleton
class ExternalEditingSteps @Inject constructor() {

	private var running = 0

	val isRunning: Boolean
		get() = running > 0

	fun started() {
		running++
	}

	fun finished() {
		running--
	}
}
