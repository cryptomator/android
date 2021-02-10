package org.cryptomator.domain.usecases.vault

import org.cryptomator.domain.CloudType
import org.cryptomator.domain.Vault
import org.cryptomator.domain.repository.VaultRepository
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito


class MoveVaultHelperTest {

	private lateinit var orderedVaults: ArrayList<Vault>
	private lateinit var unorderedVaults: ArrayList<Vault>

	private lateinit var vaultRepository: VaultRepository
	private lateinit var cloudType: CloudType

	@Test
	fun reorderVaults() {
		Mockito.`when`(vaultRepository.vaults()).thenReturn(unorderedVaults)
		assertEquals(orderedVaults, MoveVaultHelper.Companion.reorderVaults(vaultRepository), "Failed to reorderVaults")
	}

	@Test
	fun movePositionUp() {
		Mockito.`when`(vaultRepository.vaults()).thenReturn(orderedVaults)

		val resultList = ArrayList<Vault>()
		resultList.add(Vault.aVault().withId(2).withPath("").withCloudType(cloudType).withName("foo 5").withPosition(0).build())
		resultList.add(Vault.aVault().withId(3).withPath("").withCloudType(cloudType).withName("foo 10").withPosition(1).build())
		resultList.add(Vault.aVault().withId(24).withPath("").withCloudType(cloudType).withName("foo 1").withPosition(2).build())
		resultList.add(Vault.aVault().withId(4).withPath("").withCloudType(cloudType).withName("foo 15").withPosition(3).build())

		assertEquals(resultList, MoveVaultHelper.Companion.updateVaultPosition(0, 2, vaultRepository), "Failed to movePositionUp")
	}

	@Test
	fun movePositionDown() {
		Mockito.`when`(vaultRepository.vaults()).thenReturn(orderedVaults)

		val resultList2 = ArrayList<Vault>()
		resultList2.add(Vault.aVault().withId(3).withPath("").withCloudType(cloudType).withName("foo 10").withPosition(0).build())
		resultList2.add(Vault.aVault().withId(24).withPath("").withCloudType(cloudType).withName("foo 1").withPosition(1).build())
		resultList2.add(Vault.aVault().withId(2).withPath("").withCloudType(cloudType).withName("foo 5").withPosition(2).build())
		resultList2.add(Vault.aVault().withId(4).withPath("").withCloudType(cloudType).withName("foo 15").withPosition(3).build())

		assertEquals(resultList2, MoveVaultHelper.Companion.updateVaultPosition(2, 0, vaultRepository), "Failed to movePositionDown")
	}

	@Test
	fun movePositionToSelf() {
		Mockito.`when`(vaultRepository.vaults()).thenReturn(orderedVaults)

		val resultList2 = ArrayList<Vault>()
		resultList2.add(Vault.aVault().withId(24).withPath("").withCloudType(cloudType).withName("foo 1").withPosition(0).build())
		resultList2.add(Vault.aVault().withId(2).withPath("").withCloudType(cloudType).withName("foo 5").withPosition(1).build())
		resultList2.add(Vault.aVault().withId(3).withPath("").withCloudType(cloudType).withName("foo 10").withPosition(2).build())
		resultList2.add(Vault.aVault().withId(4).withPath("").withCloudType(cloudType).withName("foo 15").withPosition(3).build())

		assertEquals(resultList2, MoveVaultHelper.Companion.updateVaultPosition(1, 1, vaultRepository), "Failed to movePositionToSelf")
	}

	@Test
	fun movePositionOutOfBounds() {
		Mockito.`when`(vaultRepository.vaults()).thenReturn(orderedVaults)
		Assertions.assertThrows(IndexOutOfBoundsException::class.java) { MoveVaultHelper.Companion.updateVaultPosition(1, 4, vaultRepository) }
	}

	@Test
	fun verifyStoreInVaultRepo() {
		Mockito.`when`(vaultRepository.vaults()).thenReturn(orderedVaults)
		val result = MoveVaultHelper.Companion.updateVaultsInDatabase(orderedVaults, vaultRepository)
		assertEquals(orderedVaults, result, "Failed to verifyStoreInVaultRepo")

		orderedVaults.forEach {
			Mockito.verify(vaultRepository).store(Mockito.eq(it))
		}
	}

	@BeforeEach
	fun setup() {
		vaultRepository = Mockito.mock(VaultRepository::class.java)
		cloudType = Mockito.mock(CloudType::class.java)

		unorderedVaults = ArrayList()
		unorderedVaults.add(Vault.aVault().withId(24).withPath("").withCloudType(cloudType).withName("foo 1").withPosition(1).build())
		unorderedVaults.add(Vault.aVault().withId(3).withPath("").withCloudType(cloudType).withName("foo 10").withPosition(10).build())
		unorderedVaults.add(Vault.aVault().withId(2).withPath("").withCloudType(cloudType).withName("foo 5").withPosition(5).build())
		unorderedVaults.add(Vault.aVault().withId(4).withPath("").withCloudType(cloudType).withName("foo 15").withPosition(15).build())

		orderedVaults = ArrayList()
		orderedVaults.add(Vault.aVault().withId(24).withPath("").withCloudType(cloudType).withName("foo 1").withPosition(0).build())
		orderedVaults.add(Vault.aVault().withId(2).withPath("").withCloudType(cloudType).withName("foo 5").withPosition(1).build())
		orderedVaults.add(Vault.aVault().withId(3).withPath("").withCloudType(cloudType).withName("foo 10").withPosition(2).build())
		orderedVaults.add(Vault.aVault().withId(4).withPath("").withCloudType(cloudType).withName("foo 15").withPosition(3).build())
	}
}
