package org.cryptomator.presentation.util

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Counts the running saves of copies that Microsoft apps edited. A save outlives the browser that started it, so the
 * count is kept here, where a recreated or reopened browser finds it.
 */
@Singleton
class MicrosoftWorkaroundSaves @Inject constructor() {

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
