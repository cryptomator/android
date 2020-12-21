package org.cryptomator.domain.usecases;

import org.cryptomator.domain.usecases.cloud.Progress;
import org.cryptomator.domain.usecases.cloud.ProgressState;

/**
 * A {@link ProgressAware} {@link ResultHandler}.
 */
public abstract class ProgressAwareResultHandler<T, S extends ProgressState> implements ResultHandler<T>, ProgressAware<S> {

	public static class NoOp<T, S extends ProgressState> extends ProgressAwareResultHandler<T, S> {
		@Override
		public void onSuccess(T result) {
			// no-op
		}

		@Override
		public void onError(Throwable e) {
			// no-op
		}

		@Override
		public void onFinished() {
			// no-op
		}

		@Override
		public void onProgress(Progress<S> progress) {
			// no-op
		}
	}

	public static <T, S extends ProgressState> ProgressAwareResultHandler<T, S> from(final ResultHandler<T> resultHandler) {
		return new ProgressAwareResultHandler<T, S>() {
			@Override
			public void onProgress(Progress<S> progress) {
				// no-op
			}

			@Override
			public void onSuccess(T result) {
				resultHandler.onSuccess(result);
			}

			@Override
			public void onError(Throwable e) {
				resultHandler.onError(e);
			}

			@Override
			public void onFinished() {
				resultHandler.onFinished();
			}
		};
	}

	public static <T, S extends ProgressState> ProgressAwareResultHandler<T, S> from(final ResultHandler<T> resultHandler, final ProgressAware<S> progressAware) {
		return new ProgressAwareResultHandler<T, S>() {
			@Override
			public void onProgress(Progress<S> progress) {
				progressAware.onProgress(progress);
			}

			@Override
			public void onSuccess(T result) {
				resultHandler.onSuccess(result);
			}

			@Override
			public void onError(Throwable e) {
				resultHandler.onError(e);
			}

			@Override
			public void onFinished() {
				resultHandler.onFinished();
			}
		};
	}

}
