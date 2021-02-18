package org.cryptomator.domain.usecases.cloud;

import org.jetbrains.annotations.NotNull;

import static java.lang.Math.max;
import static java.lang.Math.min;

public class Progress<T extends ProgressState> {

	private static final int MAX_VALUE = 100;
	private static final int MIN_VALUE = 0;

	private final T state;
	private final boolean complete;
	private final int value;

	private Progress(T state, int value, boolean complete) {
		this.state = state;
		this.complete = complete;
		this.value = min(MAX_VALUE, max(MIN_VALUE, value));
	}

	public static <T extends ProgressState> ProgressBuilder<T> progress(T state) {
		return new ProgressBuilder<>(state);
	}

	public static <T extends ProgressState> Progress<T> completed() {
		return progress((T) null).between(0).and(1).thatIsCompleted();
	}

	public static <T extends ProgressState> Progress<T> completed(T state) {
		return progress(state).between(0).and(1).thatIsCompleted();
	}

	public static <T extends ProgressState> Progress<T> started(T state) {
		return progress(state).between(0).and(1).withValue(0);
	}

	public boolean stateOrCompletionChanged(Progress<T> other) {
		return other == null || //
				other.state != state || //
				other.complete != complete;
	}

	public boolean percentageChanged(Progress<T> other) {
		return other == null || //
				other.asPercentage() != asPercentage();
	}

	public int asPercentage() {
		return value;
	}

	public T state() {
		return state;
	}

	public boolean isCompleteAndHasState() {
		return complete && state != null;
	}

	public boolean isOverallComplete() {
		return complete && state == null;
	}

	@NotNull
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		if (state != null) {
			sb.append(state).append(": ");
		}
		if (complete) {
			sb.append("complete");
		} else {
			sb.append(value);
			sb.append('%');
		}
		return sb.toString();
	}

	public <S extends ProgressState> Progress<S> withState(S state) {
		return new Progress<>(state, value, complete);
	}

	public static class ProgressBuilder<T extends ProgressState> {

		private final T state;
		private long min = 0;
		private long max = 100;

		private ProgressBuilder(T state) {
			this.state = state;
		}

		public ProgressBuilder<T> between(long value) {
			min = value;
			if (max < min) {
				max = min;
			}
			return this;
		}

		public ProgressBuilder<T> and(long value) {
			max = value;
			if (max < min) {
				min = max;
			}
			return this;
		}

		public Progress<T> thatIsCompleted() {
			return withValueAndCompleted(max, true);
		}

		public Progress<T> withValue(long value) {
			return withValueAndCompleted(value, false);
		}

		private Progress<T> withValueAndCompleted(long value, boolean completed) {
			if (value >= max) {
				value = max;
			} else if (value <= min) {
				value = min;
			}
			return new Progress<>(state, (int) Math.round(100.0 * (value - min) / (max - min)), completed);
		}

	}

}
