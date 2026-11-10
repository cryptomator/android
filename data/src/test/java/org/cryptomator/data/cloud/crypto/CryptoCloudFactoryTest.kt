package org.cryptomator.data.cloud.crypto

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.CloudFolder
import org.cryptomator.domain.CloudNode
import org.cryptomator.domain.CloudType
import org.cryptomator.domain.Vault
import org.cryptomator.domain.repository.CloudContentRepository
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CryptoCloudFactoryTest {

	private val repositoryFactory: CryptoCloudContentRepositoryFactory = mock()
	private val inTest = CryptoCloudFactory(mock<CloudContentRepository<Cloud, CloudNode, CloudFolder, CloudFile>>(), repositoryFactory)
	private val stored = Vault.aVault().withId(1L).withName("vault").withPath("/vault").withCloud(cloud()).withPosition(0).build()

	@Test
	fun `the decrypted view of a vault knows what its Hub unlock found out, and keeps the cloud it was asked for`() {
		val unlocked = Vault.aCopyOf(stored).withCloud(cloud()).withHubVault(true).withHubPaidLicense(true).build()
		whenever(repositoryFactory.unlockedVault(stored)).thenReturn(unlocked)

		val vault = (inTest.decryptedViewOf(stored) as CryptoCloud).vault

		assertTrue(vault.isHubVault)
		assertTrue(vault.hasHubPaidLicense())
		assertSame(stored.cloud, vault.cloud)
	}

	@Test
	fun `the decrypted view of a locked vault is the stored vault`() {
		val vault = (inTest.decryptedViewOf(stored) as CryptoCloud).vault

		assertFalse(vault.isHubVault)
		assertFalse(vault.hasHubPaidLicense())
		assertSame(stored.cloud, vault.cloud)
	}

	private fun cloud(): Cloud {
		return mock { on { type() } doReturn CloudType.WEBDAV }
	}
}
