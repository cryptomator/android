package org.cryptomator.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LockTimeoutTest {

	@Test
	fun `a stored timeout is read back`() {
		LockTimeout.entries.forEach { assertEquals(it, LockTimeout.fromStored(it.name)) }
	}

	@Test
	fun `the former instant lock and unknown values become one minute`() {
		assertEquals(LockTimeout.ONE_MINUTE, LockTimeout.fromStored("INSTANT"))
		assertEquals(LockTimeout.ONE_MINUTE, LockTimeout.fromStored(""))
	}
}
