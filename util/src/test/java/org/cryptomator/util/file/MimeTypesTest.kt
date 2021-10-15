package org.cryptomator.util.file

import org.cryptomator.util.matchers.MimeTypeMatchers
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever


class MimeTypesTest {

	private val mimeTypeMap: MimeTypeMap = mock()
	private lateinit var inTest: MimeTypes

	@BeforeEach
	fun setup() {
		inTest = MimeTypes(mimeTypeMap)
	}

	@Test
	fun testFromExtensionReturnsEmptyOptionalIfMimeTypeMapReturnsNull() {
		whenever(mimeTypeMap.getMimeTypeFromExtension(AN_EXTENSION)).thenReturn(null)
		val result = inTest.fromExtension(AN_EXTENSION)
		assertNull(result)
	}

	@Test
	fun testFromExtensionReturnsEmptyOptionalIfMimeTypeMapReturnsInvalidMimeType() {
		whenever(mimeTypeMap.getMimeTypeFromExtension(AN_EXTENSION)).thenReturn(INVALID_MIME_TYPE)
		val result = inTest.fromExtension(AN_EXTENSION)
		assertNull(result)
	}

	@Test
	fun testFromExtensionReturnsOptionalContainingCorrectMimeType() {
		whenever(mimeTypeMap.getMimeTypeFromExtension(AN_EXTENSION)).thenReturn(VALID_MIME_TYPE)
		val result = inTest.fromExtension(AN_EXTENSION)
		MatcherAssert.assertThat(
			result, Matchers.`is`( //
				Matchers.allOf( //
					MimeTypeMatchers.hasMediatype(MEDIATYPE_OF_VALID_MIME_TYPE),  //
					MimeTypeMatchers.hasSubtype(SUBTYPE_OF_VALID_MIME_TYPE)
				)
			)
		)
	}

	@Test
	fun testFromFilenameReturnsEmptyOptionalIfMimeTypeMapReturnsNull() {
		whenever(mimeTypeMap.getMimeTypeFromExtension(AN_EXTENSION)).thenReturn(null)
		val result = inTest.fromFilename(A_FILENAME_WITH_AN_EXTENSION)
		assertNull(result)
	}

	@Test
	fun testFromFilenameReturnsEmptyOptionalIfMimeTypeMapReturnsInvalidMimeType() {
		whenever(mimeTypeMap.getMimeTypeFromExtension(AN_EXTENSION)).thenReturn(INVALID_MIME_TYPE)
		val result = inTest.fromFilename(A_FILENAME_WITH_AN_EXTENSION)
		assertNull(result)
	}

	@Test
	fun testFromFilenameReturnsEmptyOptionalIfFilenameHasNoExtension() {
		val result = inTest.fromFilename(A_FILENAME_WITHOUT_EXTENSION)
		assertNull(result)
	}

	@Test
	fun testFromFilenameReturnsOptionalContainingCorrectMimeType() {
		whenever(mimeTypeMap.getMimeTypeFromExtension(AN_EXTENSION)).thenReturn(VALID_MIME_TYPE)
		val result = inTest.fromFilename(A_FILENAME_WITH_AN_EXTENSION)
		MatcherAssert.assertThat(
			result, Matchers.`is`( //
				Matchers.allOf( //
					MimeTypeMatchers.hasMediatype(MEDIATYPE_OF_VALID_MIME_TYPE),  //
					MimeTypeMatchers.hasSubtype(SUBTYPE_OF_VALID_MIME_TYPE)
				)
			)
		)
	}

	companion object {

		private const val AN_EXTENSION = "exe"
		private const val INVALID_MIME_TYPE = "notAMimeTypeBecaseSlashIsMissing"
		private const val MEDIATYPE_OF_VALID_MIME_TYPE = "foo"
		private const val SUBTYPE_OF_VALID_MIME_TYPE = "bar"
		private const val VALID_MIME_TYPE = MEDIATYPE_OF_VALID_MIME_TYPE + '/' + SUBTYPE_OF_VALID_MIME_TYPE
		private const val A_FILENAME_WITHOUT_EXTENSION = "fooBarBaz"
		private const val A_FILENAME_WITH_AN_EXTENSION = "fooBarBaz." + AN_EXTENSION
	}
}
