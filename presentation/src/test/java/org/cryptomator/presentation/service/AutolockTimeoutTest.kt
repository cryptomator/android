package org.cryptomator.presentation.service

import org.cryptomator.util.LockTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AutolockTimeoutTest {

	private var now = 1_000_000L

	private val inTest = AutolockTimeout { now }.apply { setLockTimeout(LockTimeout.ONE_MINUTE) }

	@Test
	fun `the vaults don't lock while the app is in the foreground`() {
		inTest.setAppIsActive(true)
		now += 3_600_000

		assertTrue(inTest.isDisabled)
		assertFalse(inTest.expired())
		assertEquals(0, inTest.timeRemaining())
		assertEquals(0L, inTest.configuredTimeout())
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
		assertEquals(0, inTest.timeRemaining())
		assertEquals(0L, inTest.configuredTimeout())
	}

	// the autolock worker checks the timeout while the app changes it, so each check works on what it read first

	@Test
	fun `the app coming to the foreground during a check doesn't expire the timeout`() {
		val timeout = timeout { inTest.setAppIsActive(true) }
		inTest.setAppIsActive(false)
		inTest.setLockTimeout(timeout)
		now += 120_000

		assertFalse(inTest.expired())
	}

	@Test
	fun `the timeout turned off during a check doesn't fail the check`() {
		val timeout = timeout { inTest.setLockTimeout(LockTimeout.NEVER) }
		inTest.setAppIsActive(false)
		inTest.setLockTimeout(timeout)
		now += 120_000

		assertEquals(60_000L, inTest.configuredTimeout())
		assertEquals(0L, inTest.configuredTimeout())
	}

	/**
	 * A one-minute timeout whose first use in a check runs [duringCheck], like another thread changing the settings.
	 */
	private fun timeout(duringCheck: () -> Unit): LockTimeout {
		val timeout: LockTimeout = mock()
		whenever(timeout.durationMillis).thenReturn(60_000L)
		whenever(timeout.isDisabled).thenAnswer {
			duringCheck()
			false
		}
		return timeout
	}
}
