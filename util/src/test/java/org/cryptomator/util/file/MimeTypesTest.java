package org.cryptomator.util.file;

import org.cryptomator.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.cryptomator.util.matchers.MimeTypeMatchers.hasMediatype;
import static org.cryptomator.util.matchers.MimeTypeMatchers.hasSubtype;
import static org.cryptomator.util.matchers.OptionalMatchers.anEmptyOptional;
import static org.cryptomator.util.matchers.OptionalMatchers.anOptionalWithValueThat;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;

public class MimeTypesTest {

	private static final String AN_EXTENSION = "exe";
	private static final java.lang.String INVALID_MIME_TYPE = "notAMimeTypeBecaseSlashIsMissing";

	private static final java.lang.String MEDIATYPE_OF_VALID_MIME_TYPE = "foo";
	private static final java.lang.String SUBTYPE_OF_VALID_MIME_TYPE = "bar";
	private static final java.lang.String VALID_MIME_TYPE = MEDIATYPE_OF_VALID_MIME_TYPE + '/' + SUBTYPE_OF_VALID_MIME_TYPE;

	private static final String A_FILENAME_WITHOUT_EXTENSION = "fooBarBaz";
	private static final String A_FILENAME_WITH_AN_EXTENSION = "fooBarBaz." + AN_EXTENSION;

	private MimeTypeMap mimeTypeMap = Mockito.mock(MimeTypeMap.class);

	private MimeTypes inTest;

	@BeforeEach
	public void setup() {
		inTest = new MimeTypes(mimeTypeMap);
	}

	@Test
	public void testFromExtensionReturnsEmptyOptionalIfMimeTypeMapReturnsNull() {
		when(mimeTypeMap.getMimeTypeFromExtension(AN_EXTENSION)).thenReturn(null);

		Optional<MimeType> result = inTest.fromExtension(AN_EXTENSION);

		assertThat(result, is(anEmptyOptional()));
	}

	@Test
	public void testFromExtensionReturnsEmptyOptionalIfMimeTypeMapReturnsInvalidMimeType() {
		when(mimeTypeMap.getMimeTypeFromExtension(AN_EXTENSION)).thenReturn(INVALID_MIME_TYPE);

		Optional<MimeType> result = inTest.fromExtension(AN_EXTENSION);

		assertThat(result, is(anEmptyOptional()));
	}

	@Test
	public void testFromExtensionReturnsOptionalContainingCorrectMimeType() {
		when(mimeTypeMap.getMimeTypeFromExtension(AN_EXTENSION)).thenReturn(VALID_MIME_TYPE);

		Optional<MimeType> result = inTest.fromExtension(AN_EXTENSION);

		assertThat(result, is( //
				anOptionalWithValueThat(allOf( //
						hasMediatype(MEDIATYPE_OF_VALID_MIME_TYPE), //
						hasSubtype(SUBTYPE_OF_VALID_MIME_TYPE)))));
	}

	@Test
	public void testFromFilenameReturnsEmptyOptionalIfMimeTypeMapReturnsNull() {
		when(mimeTypeMap.getMimeTypeFromExtension(AN_EXTENSION)).thenReturn(null);

		Optional<MimeType> result = inTest.fromFilename(A_FILENAME_WITH_AN_EXTENSION);

		assertThat(result, is(anEmptyOptional()));
	}

	@Test
	public void testFromFilenameReturnsEmptyOptionalIfMimeTypeMapReturnsInvalidMimeType() {
		when(mimeTypeMap.getMimeTypeFromExtension(AN_EXTENSION)).thenReturn(INVALID_MIME_TYPE);

		Optional<MimeType> result = inTest.fromFilename(A_FILENAME_WITH_AN_EXTENSION);

		assertThat(result, is(anEmptyOptional()));
	}

	@Test
	public void testFromFilenameReturnsEmptyOptionalIfFilenameHasNoExtension() {
		Optional<MimeType> result = inTest.fromFilename(A_FILENAME_WITHOUT_EXTENSION);

		assertThat(result, is(anEmptyOptional()));
	}

	@Test
	public void testFromFilenameReturnsOptionalContainingCorrectMimeType() {
		when(mimeTypeMap.getMimeTypeFromExtension(AN_EXTENSION)).thenReturn(VALID_MIME_TYPE);

		Optional<MimeType> result = inTest.fromFilename(A_FILENAME_WITH_AN_EXTENSION);

		assertThat(result, is( //
				anOptionalWithValueThat(allOf( //
						hasMediatype(MEDIATYPE_OF_VALID_MIME_TYPE), //
						hasSubtype(SUBTYPE_OF_VALID_MIME_TYPE)))));
	}

}
