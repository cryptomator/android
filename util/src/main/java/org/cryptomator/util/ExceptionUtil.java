package org.cryptomator.util;

import java.util.Optional;
import java.util.function.Predicate;

public class ExceptionUtil {

	public static <T extends Throwable> Predicate<T> thatContainsMessage(final String message) {
		return value -> {
			String messageFromException = value.getMessage();
			return messageFromException != null && messageFromException.contains(message);
		};
	}

	public static <T extends Throwable> Predicate<T> thatHasMessage(final String message) {
		return value -> {
			String messageFromException = value.getMessage();
			return messageFromException != null && messageFromException.equals(message);
		};
	}

	public static <T extends Throwable> Optional<T> extract(final Throwable e, final Class<T> type, final Predicate<T> test) {
		if (type.isInstance(e) && test.test(type.cast(e))) {
			return Optional.ofNullable(type.cast(e));
		}
		if (e.getCause() != null) {
			return extract(e.getCause(), type, test);
		}
		return Optional.empty();
	}

	public static <T extends Throwable> Optional<T> extract(final Throwable e, final Class<T> type) {
		return extract(e, type, t -> true);
	}

	public static <T extends Throwable> boolean contains(Throwable e, Class<T> type) {
		return extract(e, type).isPresent();
	}

	public static <T extends Throwable> boolean contains(Throwable e, Class<T> type, Predicate<T> test) {
		return extract(e, type, test).isPresent();
	}
}
