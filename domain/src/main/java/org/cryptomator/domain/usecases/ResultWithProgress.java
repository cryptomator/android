package org.cryptomator.domain.usecases;

import org.cryptomator.domain.usecases.cloud.Progress;
import org.cryptomator.domain.usecases.cloud.ProgressState;

public class ResultWithProgress<T, S extends ProgressState> {

	private final Progress<S> progress;
	private final T value;

	private ResultWithProgress(T value, Progress<S> progress) {
		this.value = value;
		this.progress = progress;
	}

	public static <T, S extends ProgressState> ResultWithProgress<T, S> progress(Progress<S> progress) {
		return new ResultWithProgress<>(null, progress);
	}

	public static <T, S extends ProgressState> ResultWithProgress<T, S> finalResult(T value) {
		return new ResultWithProgress<>(value, Progress.completed());
	}

	public static <T, S extends ProgressState> ResultWithProgress<T, S> noProgress(S state) {
		return new ResultWithProgress<>(null, Progress.started(state));
	}

	public Progress<S> progress() {
		return progress;
	}

	public T value() {
		return value;
	}
}
