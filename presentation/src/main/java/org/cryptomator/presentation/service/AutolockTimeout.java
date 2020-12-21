package org.cryptomator.presentation.service;

import org.cryptomator.util.LockTimeout;

import static java.lang.System.currentTimeMillis;

class AutolockTimeout {

	private static final long APP_IS_ACTIVE = -1L;

	private volatile long appInactiveSince = APP_IS_ACTIVE;
	private LockTimeout lockTimeout = LockTimeout.ONE_MINUTE;

	private long configuredTimeout = 0;
	private long timeOfAutolock;

	public void setAppIsActive(boolean appIsActive) {
		if (appIsActive) {
			appInactiveSince = APP_IS_ACTIVE;
		} else {
			appInactiveSince = currentTimeMillis();
		}
		recompute();
	}

	public void setLockTimeout(LockTimeout lockTimeout) {
		this.lockTimeout = lockTimeout;
		recompute();
	}

	public boolean expired() {
		return !isDisabled() && timeOfAutolock <= currentTimeMillis();
	}

	public int timeRemaining() {
		if (appInactiveSince == APP_IS_ACTIVE) {
			return 0;
		}
		return (int) (timeOfAutolock - currentTimeMillis());
	}

	public long configuredTimeout() {
		return configuredTimeout;
	}

	private void recompute() {
		if (isDisabled()) {
			configuredTimeout = 0;
			timeOfAutolock = 0L;
		} else {
			configuredTimeout = lockTimeout.getDurationMillis();
			timeOfAutolock = appInactiveSince + configuredTimeout;
		}
	}

	public boolean isDisabled() {
		return appInactiveSince == APP_IS_ACTIVE || lockTimeout.isDisabled();
	}
}
