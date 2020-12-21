package org.cryptomator.domain.executor;

import java.util.Date;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import timber.log.Timber;

public class BackgroundTasks {

	private static final Lock lock = new ReentrantLock();
	private static final Condition reachedZero = lock.newCondition();

	private static final ObjectCounts counts = new ObjectCounts();

	public static class Registration {
		private final Class<?> type;
		private boolean unregistered = false;

		public Registration(Class<?> type) {
			this.type = type;
		}

		public synchronized void unregister() {
			if (unregistered) {
				return;
			}

			unregistered = true;

			lock.lock();

			try {
				int count = counts.removeAndGet(type);
				Timber.tag("BackgroundTasks").d(caller("unregister", count, counts.count()));
				if (count == 0) {
					reachedZero.signalAll();
				}
			} finally {
				lock.unlock();
			}
		}
	}

	public static void awaitCompleted() {
		lock.lock();
		try {
			Date deadline = new Date(System.currentTimeMillis() + 60_000);
			while (counts.count() > 0) {
				if (!reachedZero.awaitUntil(deadline)) {
					throw new RuntimeException("Timeout while waiting for idle async excecution");
				}
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Thread interrupted");
		} finally {
			lock.unlock();
		}
	}

	public static Registration register(Class<?> type) {
		lock.lock();

		try {
			int count = counts.addAndGet(type);
			Timber.tag("BackgroundTasks").d(caller("register", count, counts.count()));
		} finally {
			lock.unlock();
		}

		return new Registration(type);
	}

	static String caller(String what, int count, int all) {
		StackTraceElement caller = Thread.currentThread().getStackTrace()[4];
		return new StringBuilder() //
				.append("type:") //
				.append(count) //
				.append(" all:") //
				.append(all) //
				.append(' ') //
				.append(what) //
				.append("@ ") //
				.append(caller.getClassName()) //
				.append('#') //
				.append(caller.getMethodName()) //
				.append(':') //
				.append(caller.getLineNumber()) //
				.toString();
	}
}
