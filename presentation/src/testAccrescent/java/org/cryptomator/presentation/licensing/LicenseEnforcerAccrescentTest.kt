package org.cryptomator.presentation.licensing

import org.cryptomator.util.SharedPreferencesHandler
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class LicenseEnforcerAccrescentTest {

	private val sharedPreferencesHandler: SharedPreferencesHandler = mock()
	private lateinit var licenseEnforcer: LicenseEnforcer

	@BeforeEach
	fun setUp() {
		licenseEnforcer = LicenseEnforcer(sharedPreferencesHandler)
		`when`(sharedPreferencesHandler.hasRunningSubscription()).thenReturn(false)
		`when`(sharedPreferencesHandler.trialExpirationDate()).thenReturn(0L)
	}

	@Test
	fun `accrescent requires a license for write access`() {
		`when`(sharedPreferencesHandler.licenseToken()).thenReturn("")

		assertFalse(licenseEnforcer.hasPaidLicense())
		assertFalse(licenseEnforcer.hasWriteAccess())
	}

	@Test
	fun `accrescent grants write access with a license`() {
		`when`(sharedPreferencesHandler.licenseToken()).thenReturn("some-token")

		assertTrue(licenseEnforcer.hasPaidLicense())
		assertTrue(licenseEnforcer.hasWriteAccess())
	}
}
