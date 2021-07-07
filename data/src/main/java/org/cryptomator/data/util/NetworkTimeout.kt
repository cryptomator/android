package org.cryptomator.data.util

import java.util.concurrent.TimeUnit

enum class NetworkTimeout(val timeout: Long, val unit: TimeUnit) {
	CONNECTION(2L, TimeUnit.MINUTES),  //
	READ(2L, TimeUnit.MINUTES),  //
	WRITE(2L, TimeUnit.MINUTES);

	fun asMilliseconds(): Long {
		return unit.toMillis(timeout)
	}
}
