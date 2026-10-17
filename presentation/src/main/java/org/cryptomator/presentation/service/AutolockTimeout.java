package org.cryptomator.presentation.service;

import org.cryptomator.util.LockTimeout;

import java.util.function.LongSupplier;

/**
 * Tells when the vaults lock after not being used for the configured timeout, like on iOS. They are in use while the
 * app is in the foreground, and whenever another app uses them through the documents provider, see {@link #used()}.
 */
class AutolockTimeout {

	private final LongSupplier currentTimeMillis;

	private volatile boolean appIsActive = true;
	private volatile long lastUsed;
	private volatile LockTimeout lockTimeout = LockTimeout.ONE_MINUTE;

	AutolockTimeout() {
		this(System::currentTimeMillis);
	}

	AutolockTimeout(LongSupplier currentTimeMillis) {
		this.currentTimeMillis = currentTimeMillis;
		this.lastUsed = currentTimeMillis.getAsLong();
	}

	public void setAppIsActive(boolean appIsActive) {
		this.appIsActive = appIsActive;
		used();
	}

	/**
	 * Starts the timeout over, e.g. because another app used a vault or a transfer is running.
	 */
	public void used() {
		lastUsed = currentTimeMillis.getAsLong();
	}

	public void setLockTimeout(LockTimeout lockTimeout) {
		this.lockTimeout = lockTimeout;
	}

	public boolean expired() {
		return !isDisabled() && timeRemaining() <= 0;
	}

	public int timeRemaining() {
		if (isDisabled()) {
			return 0;
		}
		return (int) (lastUsed + configuredTimeout() - currentTimeMillis.getAsLong());
	}

	public long configuredTimeout() {
		return isDisabled() ? 0 : lockTimeout.getDurationMillis();
	}

	public boolean isDisabled() {
		return appIsActive || lockTimeout.isDisabled();
	}
}
