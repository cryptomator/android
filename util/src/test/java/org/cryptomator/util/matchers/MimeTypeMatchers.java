package org.cryptomator.util.matchers;

import org.cryptomator.util.file.MimeType;
import org.hamcrest.FeatureMatcher;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;

public class MimeTypeMatchers {

	public static Matcher<MimeType> hasMediatype(final String mediatype) {
		return new FeatureMatcher<MimeType, String>(Matchers.is(mediatype), "mediatype", "mediatype") {
			@Override
			protected String featureValueOf(MimeType actual) {
				return actual.getMediatype();
			}
		};
	}

	public static Matcher<MimeType> hasSubtype(final String subtype) {
		return new FeatureMatcher<MimeType, String>(Matchers.is(subtype), "subtype", "subtype") {
			@Override
			protected String featureValueOf(MimeType actual) {
				return actual.getSubtype();
			}
		};
	}

}
