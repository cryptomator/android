package org.cryptomator.presentation.service

import org.cryptomator.util.LockTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AutolockTimeoutTest {

	private var now = 1_000_000L

	private val inTest = AutolockTimeout { now }.apply { setLockTimeout(LockTimeout.ONE_MINUTE) }

	@Test
	fun `the vaults don't lock while the app is in the foreground`() {
		inTest.setAppIsActive(true)
		now += 3_600_000

		assertTrue(inTest.isDisabled)
		assertFalse(inTest.expired())
	}

	@Test
	fun `the timeout runs from when the app went to the background`() {
		inTest.setAppIsActive(false)

		now += 59_000
		assertFalse(inTest.expired())
		assertEquals(1_000, inTest.timeRemaining())
		now += 1_000
		assertTrue(inTest.expired())
	}

	@Test
	fun `a use starts the timeout over, e g by another app through the documents provider`() {
		inTest.setAppIsActive(false)
		now += 50_000

		inTest.used()

		now += 50_000
		assertFalse(inTest.expired())
		now += 10_000
		assertTrue(inTest.expired())
	}

	@Test
	fun `the vaults never lock with the timeout turned off`() {
		inTest.setAppIsActive(false)
		inTest.setLockTimeout(LockTimeout.NEVER)
		now += 3_600_000

		assertFalse(inTest.expired())
		assertEquals(0L, inTest.configuredTimeout())
	}
}
