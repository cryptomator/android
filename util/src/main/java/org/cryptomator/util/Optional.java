package org.cryptomator.util;

import java.io.Serializable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class Optional<T> implements Serializable {

	private static final Optional EMPTY = new Optional(null);
	private final T value;

	private Optional(T value) {
		this.value = value;
	}

	public static <T> Optional<T> of(T value) {
		if (value == null) {
			throw new IllegalArgumentException("Value must not be null");
		}
		return new Optional<>(value);
	}

	public static <T> Optional<T> ofNullable(T value) {
		if (value == null) {
			return empty();
		} else {
			return new Optional<>(value);
		}
	}

	public static <T> Optional<T> empty() {
		return EMPTY;
	}

	public boolean isPresent() {
		return value != null;
	}

	public void ifPresent(Consumer<? super T> consumer) {
		if (value != null) {
			consumer.accept(value);
		}
	}

	public boolean isAbsent() {
		return value == null;
	}

	public <R> Optional<R> map(Function<T, R> mapper) {
		if (isPresent()) {
			return Optional.of(mapper.apply(value));
		} else {
			return EMPTY;
		}
	}

	public T get() {
		if (isAbsent()) {
			throw new IllegalStateException("No value present");
		}
		return value;
	}

	public T orElse(T defaultValue) {
		if (isAbsent()) {
			return defaultValue;
		} else {
			return value;
		}
	}

	public T orElseGet(Supplier<T> defaultValue) {
		if (isAbsent()) {
			return defaultValue.get();
		} else {
			return value;
		}
	}

	public Optional<T> flatOrElseGet(Supplier<Optional<T>> defaultValue) {
		if (isAbsent()) {
			return defaultValue.get();
		} else {
			return this;
		}
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		return internalEquals((Optional) obj);
	}

	private boolean internalEquals(Optional o) {
		return value == null ? o.value == null : value.equals(o.value);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int hash = 1297051866;
		hash = hash * prime + (value == null ? 0 : value.hashCode());
		return hash;
	}

}
