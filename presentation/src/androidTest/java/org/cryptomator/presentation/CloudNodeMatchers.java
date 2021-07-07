package org.cryptomator.presentation;

import org.cryptomator.domain.CloudFile;
import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.CloudNode;
import com.google.common.base.Optional;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import java.util.Date;

class CloudNodeMatchers {

	public static Matcher<CloudNode> aFile(final String name) {
		return (Matcher) new TypeSafeDiagnosingMatcher<CloudFile>() {
			@Override
			protected boolean matchesSafely(CloudFile file, Description description) {
				if (name.equals(file.getName())) {
					return true;
				} else {
					description.appendText("aFile with name '").appendText(file.getName()).appendText("'");
					return false;
				}
			}

			@Override
			public void describeTo(Description description) {
				description.appendText("aFile with name '").appendText(name).appendText("'");
			}
		};
	}

	public static FileMatcher aFile() {
		return new FileMatcher();
	}

	public static Matcher<CloudNode> folder(final String name) {
		return (Matcher) new TypeSafeDiagnosingMatcher<CloudFolder>() {
			@Override
			protected boolean matchesSafely(CloudFolder file, org.hamcrest.Description description) {
				if (name.equals(file.getName())) {
					return true;
				} else {
					description.appendText("folder with name '").appendText(file.getName()).appendText("'");
					return false;
				}
			}

			@Override
			public void describeTo(org.hamcrest.Description description) {
				description.appendText("folder with name '").appendText(name).appendText("'");
			}
		};
	}

	public static class FileMatcher extends TypeSafeDiagnosingMatcher<CloudNode> {

		private String nameToCheck;
		private Optional<Long> sizeToCheck;

		private Date minModifiedToCheck;
		private Date maxModifiedToCheck;

		private FileMatcher() {
			super(CloudFile.class);
		}

		public FileMatcher withName(String name) {
			this.nameToCheck = name;
			return this;
		}

		public FileMatcher withSize(int size) {
			return withSize(Long.valueOf(size));
		}

		public FileMatcher withSize(Long size) {
			this.sizeToCheck = Optional.ofNullable(size);
			return this;
		}

		public FileMatcher withModifiedIn(Date minModified, Date maxModified) {
			this.minModifiedToCheck = minModified;
			this.maxModifiedToCheck = maxModified;
			return this;
		}

		@Override
		public void describeTo(Description description) {
			description.appendText("a file");
			if (nameToCheck != null) {
				description.appendText(" with name ").appendText(nameToCheck);
			}
			if (sizeToCheck != null) {
				description.appendText(" with size ").appendValue(sizeToCheck);
			}
			if (minModifiedToCheck != null) {
				description.appendText(" with modified in [").appendValue(minModifiedToCheck).appendText(",").appendValue(maxModifiedToCheck).appendText("]");
			}
		}

		@Override
		protected boolean matchesSafely(CloudNode cloudNode, Description description) {
			CloudFile cloudFile = (CloudFile) cloudNode;
			boolean match = true;
			description.appendText("a file");
			if (nameToCheck != null && !nameToCheck.equals(cloudFile.getName())) {
				description.appendText(" with name ").appendText(cloudFile.getName());
				match = false;
			}
			if (sizeToCheck != null && !sizeToCheck.equals(cloudFile.getSize())) {
				description.appendText(" with size ").appendValue(cloudFile.getSize());
				match = false;
			}
			if (minModifiedToCheck != null && dateInRange(minModifiedToCheck, maxModifiedToCheck, cloudFile.getModified())) {
				description.appendText(" with modified ").appendValue(cloudFile.getModified());
			}
			return match;
		}

		private boolean dateInRange(Date min, Date max, Optional<Date> modified) {
			return modified.isPresent() && !modified.get().before(min) && !modified.get().after(max);
		}
	}
}
