package org.cryptomator.data.cloud;

import org.cryptomator.domain.CloudFile;
import org.cryptomator.domain.CloudNode;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import static org.hamcrest.core.AllOf.allOf;
import static org.hamcrest.core.Is.is;

public class CloudFileMatcher extends TypeSafeDiagnosingMatcher<CloudNode> {

	private final Matcher<CloudFile> delegate;

	private CloudFileMatcher(CloudFile file) {
		super(file.getClass());

		this.delegate = allOf(Matchers.hasProperty("path", is(file.getPath())), //
				Matchers.hasProperty("modified", is(file.getModified())), //
				Matchers.hasProperty("size", is(file.getSize())), //
				Matchers.hasProperty("name", is(file.getName())), //
				Matchers.hasProperty("parent", is(file.getParent())));
	}

	public static CloudFileMatcher cloudFile(CloudFile file) {
		return new CloudFileMatcher(file);
	}

	@Override
	public void describeTo(Description description) {
		delegate.describeTo(description);
	}

	@Override
	protected boolean matchesSafely(CloudNode item, Description mismatchDescription) {

		if (delegate.matches(item)) {
			return true;
		}

		mismatchDescription.appendText("not ").appendDescriptionOf(delegate);
		return false;
	}
}
