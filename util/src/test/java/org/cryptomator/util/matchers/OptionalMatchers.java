package org.cryptomator.util.matchers;

import org.cryptomator.util.Optional;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

public class OptionalMatchers {

	public static Matcher<Optional<?>> anEmptyOptional() {
		return new TypeSafeDiagnosingMatcher<Optional<?>>() {
			@Override
			protected boolean matchesSafely(Optional<?> value, Description description) {
				if (value.isAbsent()) {
					return true;
				} else {
					description.appendText("non empty Optional");
					return false;
				}
			}

			@Override
			public void describeTo(Description description) {
				description.appendText("empty Optional");
			}
		};
	}

	public static <T> Matcher<Optional<?>> anOptionalWithValueThat(final Matcher<T> subMatcher) {
		return new TypeSafeDiagnosingMatcher<Optional<?>>() {
			@Override
			protected boolean matchesSafely(Optional<?> value, Description description) {
				if (value.isAbsent()) {
					description.appendText("empty Optional");
					return false;
				} else if (subMatcher.matches(value.get())) {
					return true;
				} else {
					description.appendText("Optional with value that not ");
					subMatcher.describeMismatch(value.get(), description);
					return false;
				}
			}

			@Override
			public void describeTo(Description description) {
				description.appendText("Optional with value that ");
				subMatcher.describeTo(description);
			}
		};
	}

}
