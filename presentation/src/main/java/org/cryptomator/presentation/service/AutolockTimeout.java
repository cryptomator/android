package org.cryptomator.presentation.service;

import android.os.SystemClock;

import org.cryptomator.util.LockTimeout;

import java.util.function.LongSupplier;

/**
 * Tells when the vaults lock after not being used for the configured timeout, like on iOS. They are in use while the
 * app is in the foreground, and whenever another app uses them through the documents provider, see {@link #used()}.
 * The timeout runs on the monotonic clock, so that setting the time back doesn't keep the vaults unlocked.
 */
class AutolockTimeout {

	private final LongSupplier elapsedRealtime;

	private volatile boolean appIsActive = true;
	private volatile long lastUsed;
	private volatile LockTimeout lockTimeout = LockTimeout.ONE_MINUTE;

	AutolockTimeout() {
		this(SystemClock::elapsedRealtime);
	}

	AutolockTimeout(LongSupplier elapsedRealtime) {
		this.elapsedRealtime = elapsedRealtime;
		this.lastUsed = elapsedRealtime.getAsLong();
	}

	public void setAppIsActive(boolean appIsActive) {
		// started over first, so that a check seeing the app in the background never counts the time it was active
		used();
		this.appIsActive = appIsActive;
	}

	/**
	 * Starts the timeout over, e.g. because another app used a vault or a transfer is running.
	 */
	public void used() {
		lastUsed = elapsedRealtime.getAsLong();
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

	/**
	 * Milliseconds until the vaults lock, 0 when overdue or disabled.
	 */
	public int timeRemaining() {
		LockTimeout timeout = lockTimeout;
		return isDisabled(timeout) ? 0 : (int) Math.max(0, remaining(timeout));
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

	private long remaining(LockTimeout timeout) {
		return lastUsed + timeout.getDurationMillis() - elapsedRealtime.getAsLong();
	}
}
