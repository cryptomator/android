package org.cryptomator.presentation.docprovider

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HelpersTest {

	@Test
	fun `conflictCopyName inserts the suffix before the extension`() {
		assertEquals("notes (1A2B3).txt", conflictCopyName("notes.txt", "1A2B3"))
	}

	@Test
	fun `conflictCopyName only treats the last extension as extension`() {
		assertEquals("archive.tar (1A2B3).gz", conflictCopyName("archive.tar.gz", "1A2B3"))
	}

	@Test
	fun `conflictCopyName appends the suffix to names without extension`() {
		assertEquals("README (1A2B3)", conflictCopyName("README", "1A2B3"))
	}

	@Test
	fun `conflictCopyName does not treat a leading dot as extension`() {
		assertEquals(".hidden (1A2B3)", conflictCopyName(".hidden", "1A2B3"))
	}

	@Test
	fun `isSameSecond ignores milliseconds`() {
		assertTrue(isSameSecond(1_000_123, 1_000_999))
	}

	@Test
	fun `isSameSecond detects different seconds`() {
		assertFalse(isSameSecond(1_000_999, 1_001_000))
	}

	@Test
	fun `isSameSecond treats unknown dates as different`() {
		assertFalse(isSameSecond(null, 1_000_000))
		assertFalse(isSameSecond(1_000_000, null))
	}
}
