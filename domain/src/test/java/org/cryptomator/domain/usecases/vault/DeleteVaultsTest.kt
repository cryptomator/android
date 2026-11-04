package org.cryptomator.domain.usecases.vault

import org.cryptomator.domain.CloudType
import org.cryptomator.domain.Vault
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.domain.repository.VaultDocumentRepository
import org.cryptomator.domain.repository.VaultRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DeleteVaultsTest {

	private val vaultRepository: VaultRepository = mock()
	private val vaultDocumentRepository: VaultDocumentRepository = mock()

	private val first = vault(1)
	private val second = vault(2)

	@Test
	fun `the copies of a removed vault are evicted once it is gone`() {
		whenever(vaultRepository.delete(first)).thenReturn(1L)

		assertEquals(1L, DeleteVault(vaultRepository, vaultDocumentRepository, first).execute())

		inOrder(vaultRepository, vaultDocumentRepository) {
			verify(vaultRepository).delete(first)
			verify(vaultDocumentRepository).evictCopies()
		}
	}

	@Test
	fun `the copies of removed vaults are evicted once they are all gone`() {
		whenever(vaultRepository.delete(first)).thenReturn(1L)
		whenever(vaultRepository.delete(second)).thenReturn(2L)

		assertEquals(listOf(1L, 2L), DeleteVaults(vaultRepository, vaultDocumentRepository, listOf(first, second)).execute())

		inOrder(vaultRepository, vaultDocumentRepository) {
			verify(vaultRepository).delete(first)
			verify(vaultRepository).delete(second)
			verify(vaultDocumentRepository).evictCopies()
		}
	}

	@Test
	fun `the copies of the vaults removed before one failed are evicted, too`() {
		whenever(vaultRepository.delete(first)).thenReturn(1L)
		whenever(vaultRepository.delete(second)).thenThrow(FatalBackendException(IllegalStateException("failed")))

		assertThrows<FatalBackendException> { DeleteVaults(vaultRepository, vaultDocumentRepository, listOf(first, second)).execute() }

		verify(vaultDocumentRepository).evictCopies()
	}

	private fun vault(id: Long): Vault {
		return Vault.aVault().withId(id).withName("vault$id").withPath("/vault$id").withCloudType(CloudType.LOCAL).withPosition(id.toInt()).build()
	}
}
