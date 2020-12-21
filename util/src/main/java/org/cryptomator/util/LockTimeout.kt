package org.cryptomator.util

import java.util.concurrent.TimeUnit

enum class LockTimeout {
	NEVER,  //
	INSTANT(500, TimeUnit.MILLISECONDS),  //
	ONE_MINUTE(1, TimeUnit.MINUTES),  //
	TWO_MINUTES(2, TimeUnit.MINUTES),  //
	FIVE_MINUTES(5, TimeUnit.MINUTES),  //
	TEN_MINUTES(10, TimeUnit.MINUTES);

	private val duration: Int
	private var unit: TimeUnit? = null
	val isDisabled: Boolean

	constructor() {
		duration = 0
		isDisabled = true
	}

	constructor(duration: Int, unit: TimeUnit) {
		this.duration = duration
		this.unit = unit
		isDisabled = false
	}

	val durationMillis: Long?
		get() {
			check(!isDisabled) { "Autolock after timeout is disabled" }
			return unit?.toMillis(duration.toLong())
		}
}
