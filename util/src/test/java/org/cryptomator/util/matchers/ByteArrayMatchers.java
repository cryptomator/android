package org.cryptomator.util.matchers;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

public class ByteArrayMatchers {

	public static Matcher<byte[]> emptyByteArray() {
		return new TypeSafeDiagnosingMatcher<byte[]>() {
			@Override
			protected boolean matchesSafely(byte[] bytes, Description description) {
				if (bytes.length == 0) {
					return true;
				} else {
					description.appendText("non empty byte array");
					return false;
				}
			}

			@Override
			public void describeTo(Description description) {
				description.appendText("empty byte array");
			}
		};
	}

}
