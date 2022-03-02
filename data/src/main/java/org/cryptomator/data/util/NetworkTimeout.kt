package org.cryptomator.data.util

import java.util.concurrent.TimeUnit

enum class NetworkTimeout(val timeout: Long, val unit: TimeUnit) {
	CONNECTION(1L, TimeUnit.MINUTES),  //
	READ(1L, TimeUnit.MINUTES),  //
	WRITE(1L, TimeUnit.MINUTES);

	fun asMilliseconds(): Long {
		return unit.toMillis(timeout)
	}
}
