package org.cryptomator.util;

public class Predicates {

	public static <T> Predicate<T> not(final Predicate<? super T> predicate) {
		return value -> !predicate.test(value);
	}

	public static <T> Predicate<T> alwaysTrue() {
		return value -> true;
	}

}
