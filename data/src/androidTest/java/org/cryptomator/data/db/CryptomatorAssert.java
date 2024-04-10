package org.cryptomator.data.db;

import android.database.Cursor;

import com.google.android.gms.common.util.Strings;

import java.text.MessageFormat;

import static org.junit.Assert.fail;

public class CryptomatorAssert {

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
				message != null && !Strings.isEmptyOrWhitespace(message) ? message : "", //
				CryptomatorDatabaseKt.stringify(expected), //
				CryptomatorDatabaseKt.stringify(actual));
		fail(failMessage);
	}
}