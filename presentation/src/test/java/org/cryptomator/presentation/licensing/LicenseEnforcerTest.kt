package org.cryptomator.presentation.licensing

import android.app.Activity
import android.content.Context
import android.widget.Toast
import org.cryptomator.presentation.R
import org.cryptomator.presentation.model.VaultModel
import org.cryptomator.util.FlavorConfig
import org.cryptomator.util.SharedPreferencesHandler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class LicenseEnforcerTest {

	private val sharedPreferencesHandler: SharedPreferencesHandler = mock()
	private lateinit var licenseEnforcer: LicenseEnforcer

	@BeforeEach
	fun setUp() {
		licenseEnforcer = LicenseEnforcer(sharedPreferencesHandler)
	}

	// -- hasWriteAccess --

	@Test
	fun `hasWriteAccess returns true when license token is present`() {
		`when`(sharedPreferencesHandler.licenseToken()).thenReturn("some-token")
		`when`(sharedPreferencesHandler.hasRunningSubscription()).thenReturn(false)
		`when`(sharedPreferencesHandler.trialExpirationDate()).thenReturn(0L)

		assertTrue(licenseEnforcer.hasWriteAccess())
	}

	@Test
	fun `hasWriteAccess returns true when subscription is active`() {
		`when`(sharedPreferencesHandler.licenseToken()).thenReturn("")
		`when`(sharedPreferencesHandler.hasRunningSubscription()).thenReturn(true)
		`when`(sharedPreferencesHandler.trialExpirationDate()).thenReturn(0L)

		assertTrue(licenseEnforcer.hasWriteAccess())
	}

	@Test
	fun `hasWriteAccess returns true when trial is active`() {
		`when`(sharedPreferencesHandler.licenseToken()).thenReturn("")
		`when`(sharedPreferencesHandler.hasRunningSubscription()).thenReturn(false)
		`when`(sharedPreferencesHandler.trialExpirationDate()).thenReturn(System.currentTimeMillis() + 86400000L)
		`when`(sharedPreferencesHandler.isTrialExpired()).thenReturn(false)

		assertTrue(licenseEnforcer.hasWriteAccess())
	}

	@Test
	fun `hasWriteAccess returns false when trial is expired`() {
		assumeTrue(!FlavorConfig.isPremiumFlavor, "Licensing logic is bypassed on this flavor")
		`when`(sharedPreferencesHandler.licenseToken()).thenReturn("")
		`when`(sharedPreferencesHandler.hasRunningSubscription()).thenReturn(false)
		`when`(sharedPreferencesHandler.trialExpirationDate()).thenReturn(System.currentTimeMillis() - 1000L)
		`when`(sharedPreferencesHandler.isTrialExpired()).thenReturn(false)

		assertFalse(licenseEnforcer.hasWriteAccess())
	}

	@Test
	fun `hasWriteAccess returns false when no license and no trial and no subscription`() {
		assumeTrue(!FlavorConfig.isPremiumFlavor, "Licensing logic is bypassed on this flavor")
		`when`(sharedPreferencesHandler.licenseToken()).thenReturn("")
		`when`(sharedPreferencesHandler.hasRunningSubscription()).thenReturn(false)
		`when`(sharedPreferencesHandler.trialExpirationDate()).thenReturn(0L)

		assertFalse(licenseEnforcer.hasWriteAccess())
	}

	// -- hasWriteAccessForVault --

	@Test
	fun `hasWriteAccessForVault returns true for non-hub vault with write access`() {
		`when`(sharedPreferencesHandler.licenseToken()).thenReturn("some-token")
		`when`(sharedPreferencesHandler.hasRunningSubscription()).thenReturn(false)
		`when`(sharedPreferencesHandler.trialExpirationDate()).thenReturn(0L)
		val vault: VaultModel = mock()
		`when`(vault.isHubVault).thenReturn(false)

		assertTrue(licenseEnforcer.hasWriteAccessForVault(vault))
	}

	@Test
	fun `hasWriteAccessForVault returns false for non-hub vault without write access`() {
		assumeTrue(!FlavorConfig.isPremiumFlavor, "Licensing logic is bypassed on this flavor")
		`when`(sharedPreferencesHandler.licenseToken()).thenReturn("")
		`when`(sharedPreferencesHandler.hasRunningSubscription()).thenReturn(false)
		`when`(sharedPreferencesHandler.trialExpirationDate()).thenReturn(0L)
		val vault: VaultModel = mock()
		`when`(vault.isHubVault).thenReturn(false)

		assertFalse(licenseEnforcer.hasWriteAccessForVault(vault))
	}

	@Test
	fun `hasWriteAccessForVault returns true for hub vault with paid license`() {
		val vault: VaultModel = mock()
		`when`(vault.isHubVault).thenReturn(true)
		`when`(vault.hasHubPaidLicense).thenReturn(true)

		assertTrue(licenseEnforcer.hasWriteAccessForVault(vault))
	}

	@Test
	fun `hasWriteAccessForVault returns false for hub vault without paid license and no local license`() {
		assumeTrue(!FlavorConfig.isPremiumFlavor, "Licensing logic is bypassed on this flavor")
		val vault: VaultModel = mock()
		`when`(vault.isHubVault).thenReturn(true)
		`when`(vault.hasHubPaidLicense).thenReturn(false)
		`when`(sharedPreferencesHandler.licenseToken()).thenReturn("")
		`when`(sharedPreferencesHandler.hasRunningSubscription()).thenReturn(false)
		`when`(sharedPreferencesHandler.trialExpirationDate()).thenReturn(0L)

		assertFalse(licenseEnforcer.hasWriteAccessForVault(vault))
	}

	@Test
	fun `hasWriteAccessForVault returns true for hub vault without paid license but with local license`() {
		val vault: VaultModel = mock()
		`when`(vault.isHubVault).thenReturn(true)
		`when`(vault.hasHubPaidLicense).thenReturn(false)
		`when`(sharedPreferencesHandler.licenseToken()).thenReturn("some-token")
		`when`(sharedPreferencesHandler.hasRunningSubscription()).thenReturn(false)
		`when`(sharedPreferencesHandler.trialExpirationDate()).thenReturn(0L)

		assertTrue(licenseEnforcer.hasWriteAccessForVault(vault))
	}

	@Test
	fun `hasWriteAccessForVault returns true for hub vault without paid license but with active trial`() {
		val vault: VaultModel = mock()
		`when`(vault.isHubVault).thenReturn(true)
		`when`(vault.hasHubPaidLicense).thenReturn(false)
		`when`(sharedPreferencesHandler.licenseToken()).thenReturn("")
		`when`(sharedPreferencesHandler.hasRunningSubscription()).thenReturn(false)
		`when`(sharedPreferencesHandler.trialExpirationDate()).thenReturn(System.currentTimeMillis() + 86400000L)
		`when`(sharedPreferencesHandler.isTrialExpired()).thenReturn(false)

		assertTrue(licenseEnforcer.hasWriteAccessForVault(vault))
	}

	@Test
	fun `ensureWriteAccessForVault returns true for hub vault without paid license but with local license`() {
		val activity: Activity = mock()
		val vault: VaultModel = mock()
		`when`(vault.isHubVault).thenReturn(true)
		`when`(vault.hasHubPaidLicense).thenReturn(false)
		`when`(sharedPreferencesHandler.licenseToken()).thenReturn("some-token")
		`when`(sharedPreferencesHandler.hasRunningSubscription()).thenReturn(false)
		`when`(sharedPreferencesHandler.trialExpirationDate()).thenReturn(0L)

		assertTrue(licenseEnforcer.ensureWriteAccessForVault(activity, vault, LicenseEnforcer.LockedAction.UPLOAD_FILES))
	}

	@Test
	fun `hasWriteAccessForVault returns true when vault is null and has write access`() {
		`when`(sharedPreferencesHandler.licenseToken()).thenReturn("some-token")
		`when`(sharedPreferencesHandler.hasRunningSubscription()).thenReturn(false)
		`when`(sharedPreferencesHandler.trialExpirationDate()).thenReturn(0L)

		assertTrue(licenseEnforcer.hasWriteAccessForVault(null))
	}

	@Test
	fun `hasWriteAccessForVault returns false when vault is null and has no write access`() {
		assumeTrue(!FlavorConfig.isPremiumFlavor, "Licensing logic is bypassed on this flavor")
		`when`(sharedPreferencesHandler.licenseToken()).thenReturn("")
		`when`(sharedPreferencesHandler.hasRunningSubscription()).thenReturn(false)
		`when`(sharedPreferencesHandler.trialExpirationDate()).thenReturn(0L)

		assertFalse(licenseEnforcer.hasWriteAccessForVault(null))
	}

	// -- ensureWriteAccessForVault --

	@Test
	fun `ensureWriteAccessForVault returns true for hub vault with paid license`() {
		val activity: Activity = mock()
		val vault: VaultModel = mock()
		`when`(vault.isHubVault).thenReturn(true)
		`when`(vault.hasHubPaidLicense).thenReturn(true)

		assertTrue(licenseEnforcer.ensureWriteAccessForVault(activity, vault, LicenseEnforcer.LockedAction.UPLOAD_FILES))
	}

	@Test
	fun `ensureWriteAccessForVault returns false for hub vault without paid license and no local license`() {
		assumeTrue(!FlavorConfig.isPremiumFlavor, "Licensing logic is bypassed on this flavor")
		val activity: Activity = mock()
		val vault: VaultModel = mock()
		`when`(vault.isHubVault).thenReturn(true)
		`when`(vault.hasHubPaidLicense).thenReturn(false)
		`when`(sharedPreferencesHandler.licenseToken()).thenReturn("")
		`when`(sharedPreferencesHandler.hasRunningSubscription()).thenReturn(false)
		`when`(sharedPreferencesHandler.trialExpirationDate()).thenReturn(0L)

		mockStatic(Toast::class.java).use { toastMock ->
			val toast: Toast = mock()
			toastMock.`when`<Toast> { Toast.makeText(activity, R.string.read_only_reason_hub_inactive, Toast.LENGTH_LONG) }.thenReturn(toast)

			assertFalse(licenseEnforcer.ensureWriteAccessForVault(activity, vault, LicenseEnforcer.LockedAction.UPLOAD_FILES))
			verify(toast).show()
		}
	}

	// -- hasPaidLicense --

	@Test
	fun `hasPaidLicense returns true when license token is present`() {
		`when`(sharedPreferencesHandler.licenseToken()).thenReturn("some-token")
		`when`(sharedPreferencesHandler.hasRunningSubscription()).thenReturn(false)

		assertTrue(licenseEnforcer.hasPaidLicense())
	}

	@Test
	fun `hasPaidLicense returns true when subscription is active`() {
		`when`(sharedPreferencesHandler.licenseToken()).thenReturn("")
		`when`(sharedPreferencesHandler.hasRunningSubscription()).thenReturn(true)

		assertTrue(licenseEnforcer.hasPaidLicense())
	}

	@Test
	fun `hasPaidLicense returns false when only trial is active`() {
		assumeTrue(!FlavorConfig.isPremiumFlavor, "Licensing logic is bypassed on this flavor")
		`when`(sharedPreferencesHandler.licenseToken()).thenReturn("")
		`when`(sharedPreferencesHandler.hasRunningSubscription()).thenReturn(false)
		`when`(sharedPreferencesHandler.trialExpirationDate()).thenReturn(System.currentTimeMillis() + 86400000L)

		assertFalse(licenseEnforcer.hasPaidLicense())
	}

	@Test
	fun `hasPaidLicense returns false when no license and no subscription`() {
		assumeTrue(!FlavorConfig.isPremiumFlavor, "Licensing logic is bypassed on this flavor")
		`when`(sharedPreferencesHandler.licenseToken()).thenReturn("")
		`when`(sharedPreferencesHandler.hasRunningSubscription()).thenReturn(false)

		assertFalse(licenseEnforcer.hasPaidLicense())
	}

	// -- hasActiveTrial --

	@Test
	fun `hasActiveTrial returns true when trial expiration is in the future`() {
		`when`(sharedPreferencesHandler.trialExpirationDate()).thenReturn(System.currentTimeMillis() + 86400000L)
		`when`(sharedPreferencesHandler.isTrialExpired()).thenReturn(false)

		assertTrue(licenseEnforcer.hasActiveTrial())
	}

	@Test
	fun `hasActiveTrial returns false when trial expiration is in the past`() {
		`when`(sharedPreferencesHandler.trialExpirationDate()).thenReturn(System.currentTimeMillis() - 1000L)
		`when`(sharedPreferencesHandler.isTrialExpired()).thenReturn(false)

		assertFalse(licenseEnforcer.hasActiveTrial())
	}

	@Test
	fun `hasActiveTrial returns false when no trial was started`() {
		`when`(sharedPreferencesHandler.trialExpirationDate()).thenReturn(0L)

		assertFalse(licenseEnforcer.hasActiveTrial())
	}

	// -- startTrial --

	@Test
	fun `startTrial sets trial expiration date 30 days in the future`() {
		`when`(sharedPreferencesHandler.trialExpirationDate()).thenReturn(0L)
		val before = System.currentTimeMillis()
		licenseEnforcer.startTrial()
		val after = System.currentTimeMillis()

		val captor = ArgumentCaptor.forClass(Long::class.java)
		verify(sharedPreferencesHandler).setTrialExpirationDate(captor.capture())

		val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000
		assertTrue(captor.value >= before + thirtyDaysMs)
		assertTrue(captor.value <= after + thirtyDaysMs)
	}

	@Test
	fun `startTrial does not overwrite existing active trial`() {
		`when`(sharedPreferencesHandler.trialExpirationDate()).thenReturn(System.currentTimeMillis() + 86400000L)

		licenseEnforcer.startTrial()

		verify(sharedPreferencesHandler, never()).setTrialExpirationDate(anyLong())
	}

	@Test
	fun `startTrial does not overwrite expired trial`() {
		`when`(sharedPreferencesHandler.trialExpirationDate()).thenReturn(System.currentTimeMillis() - 1000L)

		licenseEnforcer.startTrial()

		verify(sharedPreferencesHandler, never()).setTrialExpirationDate(anyLong())
	}

	// -- evaluateTrialState --

	@Test
	fun `evaluateTrialState returns active with formatted date when trial is active`() {
		val futureDate = System.currentTimeMillis() + 86400000L
		`when`(sharedPreferencesHandler.trialExpirationDate()).thenReturn(futureDate)
		`when`(sharedPreferencesHandler.isTrialExpired()).thenReturn(false)

		val state = licenseEnforcer.evaluateTrialState()

		assertTrue(state.isActive)
		assertFalse(state.isExpired)
		assertNotNull(state.formattedExpirationDate)
	}

	@Test
	fun `evaluateTrialState returns expired with formatted date when trial is expired`() {
		`when`(sharedPreferencesHandler.trialExpirationDate()).thenReturn(System.currentTimeMillis() - 1000L)
		`when`(sharedPreferencesHandler.isTrialExpired()).thenReturn(false)

		val state = licenseEnforcer.evaluateTrialState()

		assertFalse(state.isActive)
		assertTrue(state.isExpired)
		assertNotNull(state.formattedExpirationDate)
	}

	@Test
	fun `evaluateTrialState returns inactive and not expired when no trial started`() {
		`when`(sharedPreferencesHandler.trialExpirationDate()).thenReturn(0L)

		val state = licenseEnforcer.evaluateTrialState()

		assertFalse(state.isActive)
		assertFalse(state.isExpired)
		assertNull(state.formattedExpirationDate)
	}

	// -- sticky trial expiry --

	@Test
	fun `hasActiveTrial latches isTrialExpired when expiration is in the past`() {
		`when`(sharedPreferencesHandler.trialExpirationDate()).thenReturn(System.currentTimeMillis() - 1000L)
		`when`(sharedPreferencesHandler.isTrialExpired()).thenReturn(false)

		assertFalse(licenseEnforcer.hasActiveTrial())
		verify(sharedPreferencesHandler).setTrialExpired(true)
	}

	@Test
	fun `hasActiveTrial returns false when isTrialExpired is already true even if date is in the future`() {
		`when`(sharedPreferencesHandler.trialExpirationDate()).thenReturn(System.currentTimeMillis() + 86400000L)
		`when`(sharedPreferencesHandler.isTrialExpired()).thenReturn(true)

		assertFalse(licenseEnforcer.hasActiveTrial())
		verify(sharedPreferencesHandler, never()).setTrialExpired(anyBoolean())
	}

	@Test
	fun `evaluateTrialState latches isTrialExpired and returns expired when date is in the past`() {
		`when`(sharedPreferencesHandler.trialExpirationDate()).thenReturn(System.currentTimeMillis() - 1000L)
		`when`(sharedPreferencesHandler.isTrialExpired()).thenReturn(false)

		val state = licenseEnforcer.evaluateTrialState()

		assertFalse(state.isActive)
		assertTrue(state.isExpired)
		verify(sharedPreferencesHandler).setTrialExpired(true)
	}

	@Test
	fun `evaluateTrialState returns expired when isTrialExpired is already true even if date is in the future`() {
		`when`(sharedPreferencesHandler.trialExpirationDate()).thenReturn(System.currentTimeMillis() + 86400000L)
		`when`(sharedPreferencesHandler.isTrialExpired()).thenReturn(true)

		val state = licenseEnforcer.evaluateTrialState()

		assertFalse(state.isActive)
		assertTrue(state.isExpired)
		assertNotNull(state.formattedExpirationDate)
		verify(sharedPreferencesHandler, never()).setTrialExpired(anyBoolean())
	}

	@Test
	fun `observeTrialExpiry idempotent once flag is set`() {
		`when`(sharedPreferencesHandler.trialExpirationDate()).thenReturn(System.currentTimeMillis() - 1000L)
		`when`(sharedPreferencesHandler.isTrialExpired()).thenReturn(true)

		licenseEnforcer.hasActiveTrial()
		licenseEnforcer.evaluateTrialState()

		verify(sharedPreferencesHandler, never()).setTrialExpired(anyBoolean())
	}

	@Test
	fun `startTrial does not reset sticky trialExpired flag`() {
		`when`(sharedPreferencesHandler.trialExpirationDate()).thenReturn(0L)
		`when`(sharedPreferencesHandler.isTrialExpired()).thenReturn(true)

		licenseEnforcer.startTrial()

		verify(sharedPreferencesHandler, never()).setTrialExpired(anyBoolean())
	}

	@Test
	fun `fresh install with no trial and no flag behaves unchanged`() {
		`when`(sharedPreferencesHandler.trialExpirationDate()).thenReturn(0L)
		`when`(sharedPreferencesHandler.isTrialExpired()).thenReturn(false)

		assertFalse(licenseEnforcer.hasActiveTrial())
		val state = licenseEnforcer.evaluateTrialState()
		assertFalse(state.isActive)
		assertFalse(state.isExpired)
		assertNull(state.formattedExpirationDate)
		verify(sharedPreferencesHandler, never()).setTrialExpired(anyBoolean())
	}

	// -- LockedAction --

	@Test
	fun `LockedAction fromName returns correct enum for each defined value`() {
		LicenseEnforcer.LockedAction.values().forEach { action ->
			assertNotNull(LicenseEnforcer.LockedAction.fromName(action.name))
			assertEquals(action, LicenseEnforcer.LockedAction.fromName(action.name))
		}
	}

	@Test
	fun `LockedAction fromName returns null for null input`() {
		assertNull(LicenseEnforcer.LockedAction.fromName(null))
	}

	@Test
	fun `LockedAction fromName returns null for unknown name`() {
		assertNull(LicenseEnforcer.LockedAction.fromName("UNKNOWN_ACTION"))
	}

	@Test
	fun `LockedAction fromName returns null for empty string`() {
		assertNull(LicenseEnforcer.LockedAction.fromName(""))
	}

	@Test
	fun `LockedAction fromName is case-sensitive and does not match lowercase`() {
		assertNull(LicenseEnforcer.LockedAction.fromName("create_vault"))
		assertNull(LicenseEnforcer.LockedAction.fromName("upload_files"))
	}

	@Test
	fun `LockedAction CREATE_VAULT has correct name`() {
		assertEquals("CREATE_VAULT", LicenseEnforcer.LockedAction.CREATE_VAULT.name)
	}

	@Test
	fun `LockedAction UPLOAD_FILES resolves from name`() {
		val action = LicenseEnforcer.LockedAction.fromName("UPLOAD_FILES")
		assertNotNull(action)
		assertEquals(LicenseEnforcer.LockedAction.UPLOAD_FILES, action)
	}

	@Test
	fun `LockedAction DELETE_NODE resolves from name`() {
		val action = LicenseEnforcer.LockedAction.fromName("DELETE_NODE")
		assertNotNull(action)
		assertEquals(LicenseEnforcer.LockedAction.DELETE_NODE, action)
	}

	// -- evaluateUiState --

	@Test
	fun `evaluateUiState returns active trial with expiration text`() {
		assumeTrue(!FlavorConfig.isPremiumFlavor, "Licensing logic is bypassed on this flavor")
		val context: Context = mock()
		`when`(sharedPreferencesHandler.licenseToken()).thenReturn("")
		`when`(sharedPreferencesHandler.hasRunningSubscription()).thenReturn(false)
		`when`(sharedPreferencesHandler.trialExpirationDate()).thenReturn(System.currentTimeMillis() + 86400000L)
		`when`(sharedPreferencesHandler.isTrialExpired()).thenReturn(false)
		`when`(context.getString(eq(R.string.screen_license_check_trial_expiration), any())).thenReturn("Expiration Date: Mar 28, 2026")

		val uiState = licenseEnforcer.evaluateUiState(context)

		assertTrue(uiState.hasWriteAccess)
		assertFalse(uiState.hasPaidLicense)
		assertTrue(uiState.trialState.isActive)
		assertNotNull(uiState.trialExpirationText)
	}

	@Test
	fun `evaluateUiState returns expired trial with expiration date text`() {
		assumeTrue(!FlavorConfig.isPremiumFlavor, "Licensing logic is bypassed on this flavor")
		val context: Context = mock()
		`when`(sharedPreferencesHandler.licenseToken()).thenReturn("")
		`when`(sharedPreferencesHandler.hasRunningSubscription()).thenReturn(false)
		`when`(sharedPreferencesHandler.trialExpirationDate()).thenReturn(System.currentTimeMillis() - 1000L)
		`when`(sharedPreferencesHandler.isTrialExpired()).thenReturn(false)
		`when`(context.getString(eq(R.string.screen_license_check_trial_expiration), any())).thenReturn("Expiration Date: Mar 26, 2026")

		val uiState = licenseEnforcer.evaluateUiState(context)

		assertFalse(uiState.hasWriteAccess)
		assertFalse(uiState.hasPaidLicense)
		assertTrue(uiState.trialState.isExpired)
		assertNotNull(uiState.trialExpirationText)
	}

	@Test
	fun `evaluateUiState returns null expiration text when no trial started`() {
		assumeTrue(!FlavorConfig.isPremiumFlavor, "Licensing logic is bypassed on this flavor")
		val context: Context = mock()
		`when`(sharedPreferencesHandler.licenseToken()).thenReturn("")
		`when`(sharedPreferencesHandler.hasRunningSubscription()).thenReturn(false)
		`when`(sharedPreferencesHandler.trialExpirationDate()).thenReturn(0L)

		val uiState = licenseEnforcer.evaluateUiState(context)

		assertFalse(uiState.hasWriteAccess)
		assertFalse(uiState.hasPaidLicense)
		assertFalse(uiState.trialState.isActive)
		assertFalse(uiState.trialState.isExpired)
		assertNull(uiState.trialExpirationText)
	}
}