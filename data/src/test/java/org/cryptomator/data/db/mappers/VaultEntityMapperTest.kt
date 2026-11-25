package org.cryptomator.data.db.mappers

import org.cryptomator.domain.CloudType
import org.cryptomator.domain.Vault
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VaultEntityMapperTest {

	private val mapper = VaultEntityMapper(CloudEntityMapper())

	private val vault = Vault.aVault().withId(1L).withName("vault").withPath("/vault").withCloudType(CloudType.LOCAL).withPosition(0).build()

	@Test
	fun `a stored Hub vault knows whether its last unlock found a paid license`() {
		val paid = mapper.toEntity(Vault.aCopyOf(vault).withHubVault(true).withHubPaidLicense(true).build())
		val unpaid = mapper.toEntity(Vault.aCopyOf(vault).withHubVault(true).withHubPaidLicense(false).build())

		assertEquals(true, paid.vault.hubPaidLicense)
		assertEquals(false, unpaid.vault.hubPaidLicense)
		mapper.fromEntity(paid).let {
			assertTrue(it.isHubVault)
			assertTrue(it.hasHubPaidLicense())
		}
		mapper.fromEntity(unpaid).let {
			assertTrue(it.isHubVault)
			assertFalse(it.hasHubPaidLicense())
		}
	}

	@Test
	fun `a stored vault that isn't a Hub vault has no Hub license`() {
		// a paid license without a Hub vault doesn't occur, and isn't stored
		val entity = mapper.toEntity(Vault.aCopyOf(vault).withHubPaidLicense(true).build())

		assertNull(entity.vault.hubPaidLicense)
		mapper.fromEntity(entity).let {
			assertFalse(it.isHubVault)
			assertFalse(it.hasHubPaidLicense())
		}
	}
}
