package org.cryptomator.util.concurrent;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import androidx.annotation.NonNull;

public class CompletableFuture<T> implements Future<T> {

	private final Lock lock = new ReentrantLock();
	private final Condition completeOrCancelled = lock.newCondition();

	private volatile boolean cancelled;
	private volatile boolean done;

	private T result;
	private ExecutionException error;

	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		lock.lock();
		try {
			if (!isDone()) {
				cancelled = true;
				completeOrCancelled.signal();
			}
		} finally {
			lock.unlock();
		}
		return cancelled;
	}

	public void complete(T result) {
		lock.lock();
		try {
			if (isDone()) {
				throw new IllegalStateException("Already completed");
			}
			done = true;
			this.result = result;
			completeOrCancelled.signal();
		} finally {
			lock.unlock();
		}
	}

	public void fail(Throwable e) {
		lock.lock();
		try {
			if (isDone()) {
				throw new IllegalStateException("Already completed");
			}
			done = true;
			this.error = new ExecutionException(e);
			completeOrCancelled.signal();
		} finally {
			lock.unlock();
		}
	}

	@Override
	public boolean isCancelled() {
		return cancelled;
	}

	@Override
	public boolean isDone() {
		return done || cancelled;
	}

	@Override
	public T get() throws InterruptedException, ExecutionException {
		lock.lock();
		try {
			if (!isDone()) {
				completeOrCancelled.await();
			}
			return getInternal();
		} finally {
			lock.unlock();
		}
	}

	@Override
	public T get(long timeout, @NonNull TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
		lock.lock();
		try {
			if (!isDone() && !completeOrCancelled.await(timeout, unit)) {
				throw new TimeoutException();
			}
			return getInternal();
		} finally {
			lock.unlock();
		}
	}

	private T getInternal() throws ExecutionException {
		if (cancelled) {
			throw new CancellationException();
		}
		if (done) {
			if (error != null) {
				throw error;
			}
			return result;
		}
		throw new IllegalStateException("Future not done but should be");
	}

}
