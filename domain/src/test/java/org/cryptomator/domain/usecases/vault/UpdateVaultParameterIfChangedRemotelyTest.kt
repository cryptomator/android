package org.cryptomator.domain.usecases.vault

import org.cryptomator.domain.CloudType
import org.cryptomator.domain.Vault
import org.cryptomator.domain.repository.VaultRepository
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.same
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class UpdateVaultParameterIfChangedRemotelyTest {

	private val vaultRepository: VaultRepository = mock()

	private val stored = Vault.aVault().withId(1L).withName("vault").withPath("/vault").withCloudType(CloudType.LOCAL).withPosition(0).withFormat(8).withShorteningThreshold(220).build()

	private fun unlockedAs(unlocked: Vault, storedAs: Vault = stored): Vault {
		whenever(vaultRepository.load(1L)).thenReturn(storedAs)
		whenever(vaultRepository.store(unlocked)).thenReturn(unlocked)
		return UpdateVaultParameterIfChangedRemotely(vaultRepository, unlocked).execute()
	}

	@Test
	fun `a vault that was unlocked as it is stored isn't stored again`() {
		val unlocked = Vault.aCopyOf(stored).withUnlocked(true).build()

		assertSame(unlocked, unlockedAs(unlocked))

		verify(vaultRepository, never()).store(any())
	}

	@Test
	fun `a vault whose format changed is stored`() {
		val unlocked = Vault.aCopyOf(stored).withFormat(7).build()

		unlockedAs(unlocked)

		verify(vaultRepository).store(same(unlocked))
	}

	@Test
	fun `a vault whose shortening threshold changed is stored`() {
		val unlocked = Vault.aCopyOf(stored).withShorteningThreshold(36).build()

		unlockedAs(unlocked)

		verify(vaultRepository).store(same(unlocked))
	}

	@Test
	fun `a vault that was unlocked as a Hub vault is stored, so that it is known as one while it is locked`() {
		val unlocked = Vault.aCopyOf(stored).withHubVault(true).withHubPaidLicense(false).build()

		unlockedAs(unlocked)

		verify(vaultRepository).store(same(unlocked))
	}

	@Test
	fun `a Hub vault whose license changed since its last unlock is stored`() {
		val storedHubVault = Vault.aCopyOf(stored).withHubVault(true).withHubPaidLicense(true).build()
		val unlocked = Vault.aCopyOf(storedHubVault).withHubPaidLicense(false).build()

		unlockedAs(unlocked, storedHubVault)

		verify(vaultRepository).store(same(unlocked))
	}

	@Test
	fun `a Hub vault whose license is as its last unlock found it isn't stored again`() {
		val storedHubVault = Vault.aCopyOf(stored).withHubVault(true).withHubPaidLicense(true).build()
		val unlocked = Vault.aCopyOf(storedHubVault).withUnlocked(true).build()

		unlockedAs(unlocked, storedHubVault)

		verify(vaultRepository, never()).store(any())
	}

	@Test
	fun `a vault that is no longer unlocked as a Hub vault is stored, so that its Hub license ends`() {
		val storedHubVault = Vault.aCopyOf(stored).withHubVault(true).withHubPaidLicense(true).build()
		val unlocked = Vault.aCopyOf(storedHubVault).withHubVault(false).withHubPaidLicense(false).build()

		unlockedAs(unlocked, storedHubVault)

		verify(vaultRepository).store(same(unlocked))
	}
}
