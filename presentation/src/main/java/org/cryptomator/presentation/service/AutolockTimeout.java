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

	// the fields are read once per call: the autolock worker polls while the app changes them, and asking a disabled
	// timeout for its duration throws

	public boolean expired() {
		LockTimeout timeout = lockTimeout;
		return !isDisabled(timeout) && remaining(timeout) <= 0;
	}

	public int timeRemaining() {
		LockTimeout timeout = lockTimeout;
		return isDisabled(timeout) ? 0 : remaining(timeout);
	}

	public long configuredTimeout() {
		LockTimeout timeout = lockTimeout;
		return isDisabled(timeout) ? 0 : timeout.getDurationMillis();
	}

	public boolean isDisabled() {
		return isDisabled(lockTimeout);
	}

	private boolean isDisabled(LockTimeout timeout) {
		return appIsActive || timeout.isDisabled();
	}

	private int remaining(LockTimeout timeout) {
		return (int) (lastUsed + timeout.getDurationMillis() - currentTimeMillis.getAsLong());
	}
}
