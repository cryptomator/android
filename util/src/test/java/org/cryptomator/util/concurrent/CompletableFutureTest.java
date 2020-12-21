package org.cryptomator.util.concurrent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class CompletableFutureTest {

	private static final long ASYNC_DELAY = 50; // [ms]
	private static final long DELAY_BEFORE_ASYNC_COMPLETED = ASYNC_DELAY - 40; // [ms]
	private static final long DELAY_AFTER_ASYNC_COMPLETD = ASYNC_DELAY + 50; // [ms]

	private static final Object AN_OBJECT = new Object();

	private final Collection<AsyncOperation> asyncOperations = new ArrayList<>();

	private final CompletableFuture<Object> inTest = new CompletableFuture<>();

	@AfterEach
	public void tearDown() {
		for (AsyncOperation asyncOperation : asyncOperations) {
			asyncOperation.await();
		}
	}

	@Test
	public void testCompletedFutureCanNotBeCompleted() {
		inTest.complete(AN_OBJECT);

		Assertions.assertThrows(IllegalStateException.class, () -> inTest.complete(AN_OBJECT));
	}

	@Test
	public void testFailedFutureCanNotBeCompleted() {
		inTest.fail(new Throwable());

		Assertions.assertThrows(IllegalStateException.class, () -> inTest.complete(AN_OBJECT));
	}

	@Test
	public void testCancelledFutureCanNotBeCompleted() {
		inTest.cancel(false);

		Assertions.assertThrows(IllegalStateException.class, () -> inTest.complete(AN_OBJECT));
	}

	@Test
	public void testCompletedFutureCanNotBeFailed() {
		inTest.complete(AN_OBJECT);

		Assertions.assertThrows(IllegalStateException.class, () -> inTest.fail(new Throwable()));
	}

	@Test
	public void testFailedFutureCanNotBeFailed() {
		inTest.fail(new Throwable());

		Assertions.assertThrows(IllegalStateException.class, () -> inTest.fail(new Throwable()));
	}

	@Test
	public void testCancelledFutureCanNotBeFailed() {
		inTest.cancel(false);

		Assertions.assertThrows(IllegalStateException.class, () -> inTest.fail(new Throwable()));
	}

	@Test
	public void testFutureReturnsTrueWhenCancelled() {
		assertThat(inTest.cancel(false), is(true));
	}

	@Test
	public void testCompletedFutureReturnsFalseWhenCancelled() {
		inTest.complete(AN_OBJECT);

		assertThat(inTest.cancel(false), is(false));
	}

	@Test
	public void testFailedFutureReturnsFalseWhenCancelled() {
		inTest.fail(new Throwable());

		assertThat(inTest.cancel(false), is(false));
	}

	@Test
	public void testCancelledFutureReturnsTrueWhenCancelled() {
		inTest.cancel(false);

		assertThat(inTest.cancel(false), is(true));
	}

	@Test
	public void testCompletedFutureReturnsResultFromGet() throws ExecutionException, InterruptedException {
		inTest.complete(AN_OBJECT);

		Object result = inTest.get();

		assertThat(result, is(AN_OBJECT));
	}

	@Test
	public void testFailedFutureThrowsExceptionFromGet() {
		Throwable expectedCause = new Throwable();
		inTest.fail(expectedCause);

		ExecutionException exception = Assertions.assertThrows(ExecutionException.class, inTest::get);
		assertThat(expectedCause, is(exception.getCause()));
	}

	@Test
	public void testCancelledFutureThrowsExceptionFromGet() {
		inTest.cancel(false);

		Assertions.assertThrows(CancellationException.class, inTest::get);
	}

	@Test
	public void testCompletedFutureReturnsResultFromGetWithTimeout() throws ExecutionException, InterruptedException, TimeoutException {
		inTest.complete(AN_OBJECT);

		Object result = inTest.get(1, TimeUnit.MILLISECONDS);

		assertThat(result, is(AN_OBJECT));
	}

	@Test
	public void testFailedFutureThrowsExceptionFromGetWithTimeout() {
		Throwable expectedCause = new Throwable();
		inTest.fail(expectedCause);

		ExecutionException exception = Assertions.assertThrows(ExecutionException.class, () -> inTest.get(1, TimeUnit.MILLISECONDS));
		assertThat(expectedCause, is(exception.getCause()));
	}

	@Test
	public void testCancelledFutureThrowsExceptionFromGetWithTimeout() {
		inTest.cancel(false);

		Assertions.assertThrows(CancellationException.class, () -> inTest.get(1, TimeUnit.MILLISECONDS));
	}

	@Test
	public void testFutureCompletedAsyncReturnsResultFromGet() throws ExecutionException, InterruptedException {
		runAsyncAndDelayed(() -> inTest.complete(AN_OBJECT));

		Object result = inTest.get();

		assertThat(result, is(AN_OBJECT));
	}

	@Test
	public void testFutureFailedAsyncThrowsExceptionFromGet() {
		final Throwable expectedCause = new Throwable();
		runAsyncAndDelayed(() -> inTest.fail(expectedCause));

		ExecutionException exception = Assertions.assertThrows(ExecutionException.class, inTest::get);
		assertThat(expectedCause, is(exception.getCause()));
	}

	@Test
	public void testFutureCancelledAsyncThrowsExceptionFromGet() {
		runAsyncAndDelayed(() -> inTest.cancel(false));

		Assertions.assertThrows(CancellationException.class, inTest::get);
	}

	@Test
	public void testFutureCompletedAsyncReturnsResultFromGetWithTimeout() throws ExecutionException, InterruptedException, TimeoutException {
		runAsyncAndDelayed(() -> inTest.complete(AN_OBJECT));

		Object result = inTest.get(DELAY_AFTER_ASYNC_COMPLETD, TimeUnit.MILLISECONDS);

		assertThat(result, is(AN_OBJECT));
	}

	@Test
	public void testFutureFailedAsyncThrowsExceptionFromGetWithTimeout() {
		final Throwable expectedCause = new Throwable();
		runAsyncAndDelayed(() -> inTest.fail(expectedCause));

		ExecutionException exception = Assertions.assertThrows(ExecutionException.class, () -> inTest.get(DELAY_AFTER_ASYNC_COMPLETD, TimeUnit.MILLISECONDS));
		assertThat(expectedCause, is(exception.getCause()));
	}

	@Test
	public void testFutureCancelledAsyncThrowsExceptionFromGetWithTimeout() {
		runAsyncAndDelayed(() -> inTest.cancel(false));

		Assertions.assertThrows(CancellationException.class, () -> inTest.get(DELAY_AFTER_ASYNC_COMPLETD, TimeUnit.MILLISECONDS));
	}

	@Test
	public void testFutureCompletedNotYetThrowsTimeoutExceptionFromGetWithTimeout() {
		runAsyncAndDelayed(() -> inTest.complete(AN_OBJECT));

		Assertions.assertThrows(TimeoutException.class, () -> inTest.get(DELAY_BEFORE_ASYNC_COMPLETED, TimeUnit.MILLISECONDS));
	}

	@Test
	public void testFutureFailedNotYetThrowsTimeoutExceptionFromGetWithTimeout() {
		final Throwable expectedCause = new Throwable();
		runAsyncAndDelayed(() -> inTest.fail(expectedCause));

		Assertions.assertThrows(TimeoutException.class, () -> inTest.get(DELAY_BEFORE_ASYNC_COMPLETED, TimeUnit.MILLISECONDS));
	}

	@Test
	public void testFutureCancelledNotYetThrowsTimeoutExceptionFromGetWithTimeout() {
		runAsyncAndDelayed(() -> inTest.cancel(false));

		Assertions.assertThrows(TimeoutException.class, () -> inTest.get(DELAY_BEFORE_ASYNC_COMPLETED, TimeUnit.MILLISECONDS));
	}

	private void runAsyncAndDelayed(final Runnable task) {
		final Thread thread = new Thread(() -> {
			try {
				Thread.sleep(ASYNC_DELAY);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
			task.run();
		});
		thread.start();
		asyncOperations.add(() -> {
			try {
				thread.join();
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		});
	}

	private interface AsyncOperation {

		void await();

	}

}
