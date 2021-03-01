package org.cryptomator.data.cloud;

import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.CloudNode;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import static org.hamcrest.core.AllOf.allOf;
import static org.hamcrest.core.Is.is;

public class CloudFolderMatcher extends TypeSafeDiagnosingMatcher<CloudNode> {

	private final Matcher<CloudFolder> delegate;

	private CloudFolderMatcher(CloudFolder folder) {
		super(folder.getClass());

		this.delegate = allOf(Matchers.hasProperty("path", is(folder.getPath())), //
				Matchers.hasProperty("name", is(folder.getName())), //
				Matchers.hasProperty("parent", is(folder.getParent())));
	}

	public static CloudFolderMatcher cloudFolder(CloudFolder folder) {
		return new CloudFolderMatcher(folder);
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
