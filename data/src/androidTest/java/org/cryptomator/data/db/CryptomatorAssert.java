package org.cryptomator.data.db;

import android.database.Cursor;

import com.google.android.gms.common.util.Strings;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static org.junit.Assert.fail;

public class CryptomatorAssert {

	private final static Pattern UUID_PATTERN = Pattern.compile("^\\p{XDigit}{8}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{12}$");

	private CryptomatorAssert() {
	}

	public static void assertCursorEquals(Cursor expected, Cursor actual) {
		assertCursorEquals(null, expected, actual);
	}

	public static void assertCursorEquals(String message, Cursor expected, Cursor actual) {
		if ((expected == null) != (actual == null)) {
			failCursorNotEquals(message, expected, actual);
			return;
		}
		if (expected == null || CryptomatorDatabaseKt.equalsCursor(expected, actual)) {
			return;
		}

		failCursorNotEquals(message, expected, actual);
	}

	private static void failCursorNotEquals(String message, Cursor expected, Cursor actual) {
		String failMessage = MessageFormat.format("{0}\n" + //
						"---------- expected ----------\n" + //
						"{1}\n" + //
						"---------- but was ----------\n" + //
						"{2}", //
				message != null && !Strings.isEmptyOrWhitespace(message) ? message : "Cursors are not equal", //
				CryptomatorDatabaseKt.stringify(expected), //
				CryptomatorDatabaseKt.stringify(actual));
		fail(failMessage);
	}

	public static void assertIsUUID(String actual) {
		assertIsUUID(null, actual);
	}

	public static void assertIsUUID(String message, String actual) {
		if (actual != null && UUID_PATTERN.matcher(actual).matches()) {
			return;
		}
		String failMessage = MessageFormat.format("{0}: {1}", //
				message != null && !Strings.isEmptyOrWhitespace(message) ? message : "String is not a valid UUID", //
				actual != null ? '"' + actual + '"' : "<null>");
		fail(failMessage);
	}

	public static void assertOrder(Order order, int firstStep, int... moreSteps) {
		order.assertOrder(firstStep, moreSteps);
	}

	public static class Order {

		private final Object lock = new Object();
		private final Map<Integer, List<Integer>> recognized = new HashMap<>();

		private int currentStep = 0;

		public void assertOrder(int firstStep, int... moreSteps) {
			List<Integer> steps = IntStream.concat( //
					IntStream.of(firstStep), //
					Arrays.stream(moreSteps) //
			).boxed().toList();

			assertOrder(steps);
		}

		private void assertOrder(List<Integer> steps) {
			synchronized (lock) {
				for (Integer step : steps) {
					List<Integer> existing = recognized.get(step);
					if (existing != null) {
						if (!existing.equals(steps)) {
							fail("Step has been assigned twice!");
						}
					} else {
						recognized.put(step, steps);
					}
				}
				if (!steps.contains(currentStep)) {
					fail("Expected step was any of %s; current step is <%d>".formatted(steps, currentStep));
				}
				currentStep++;
			}
		}
	}
}