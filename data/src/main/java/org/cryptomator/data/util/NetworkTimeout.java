package org.cryptomator.data.util;

import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.MINUTES;

public enum NetworkTimeout {

	CONNECTION(2L, MINUTES), //
	READ(2L, MINUTES), //
	WRITE(2L, MINUTES);

	private final long timeout;
	private final TimeUnit unit;

	NetworkTimeout(long timeout, TimeUnit unit) {
		this.timeout = timeout;
		this.unit = unit;
	}

	public long getTimeout() {
		return timeout;
	}

	public TimeUnit getUnit() {
		return unit;
	}

	public long asMilliseconds() {
		return unit.toMillis(timeout);
	}
}
