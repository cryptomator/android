package org.cryptomator.util.file;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import static org.cryptomator.util.file.MimeType.WILDCARD_MEDIATYPE;
import static org.cryptomator.util.file.MimeType.WILDCARD_SUBTYPE;
import static org.cryptomator.util.matchers.MimeTypeMatchers.hasMediatype;
import static org.cryptomator.util.matchers.MimeTypeMatchers.hasSubtype;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MimeTypeTest {

	private static final MimeType A_MIME_TYPE = new MimeType("foo", "bar");
	private static final MimeType ANOTHER_EQUAL_MIME_TYPE = new MimeType("foo", "bar");
	private static final MimeType ANOTHER_MIME_TYPE_WITH_EQUAL_MEDIATYPE = new MimeType("foo", "baz");
	private static final MimeType ANOTHER_MIME_TYPE_WITH_OTHER_MEDIATYPE = new MimeType("baz", "foo");

	@Test
	public void testHasMatchingMediatypeReturnsTrueIfMediatypeMatches() {
		assertTrue(A_MIME_TYPE.hasMatchingMediatype(ANOTHER_MIME_TYPE_WITH_EQUAL_MEDIATYPE));
	}

	@Test
	public void testHasMatchingMediatypeReturnsFalseIfMediatypeDoesNotMatch() {
		assertFalse(A_MIME_TYPE.hasMatchingMediatype(ANOTHER_MIME_TYPE_WITH_OTHER_MEDIATYPE));
	}

	@Test
	public void testCombineReturnsEqualValieWhenInvokedWithEqualMimeType() {
		MimeType result = A_MIME_TYPE.combine(ANOTHER_EQUAL_MIME_TYPE);

		assertThat(result, Matchers.allOf(hasMediatype("foo"), hasSubtype("bar")));
	}

	@Test
	public void testCombineReturnsMimeTypeWithWildcardSubtypeWhenInvokedWithMimeTypeWithMatchingMediatypeButDifferingSubtype() {
		MimeType result = A_MIME_TYPE.combine(ANOTHER_MIME_TYPE_WITH_EQUAL_MEDIATYPE);

		assertThat(result, Matchers.allOf(hasMediatype("foo"), hasSubtype(WILDCARD_SUBTYPE)));
	}

	@Test
	public void testCombineReturnsWildcardMimeTypeWhenInvokedWithMimeTypeWithDifferingMediaAndSubtype() {
		MimeType result = A_MIME_TYPE.combine(ANOTHER_MIME_TYPE_WITH_OTHER_MEDIATYPE);

		assertThat(result, Matchers.allOf(hasMediatype(WILDCARD_MEDIATYPE), hasSubtype(WILDCARD_SUBTYPE)));
	}

}
