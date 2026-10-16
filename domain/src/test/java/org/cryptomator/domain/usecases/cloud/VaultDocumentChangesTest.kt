package org.cryptomator.domain.usecases.cloud

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.CloudFolder
import org.cryptomator.domain.CloudNode
import org.cryptomator.domain.CloudType
import org.cryptomator.domain.Vault
import org.cryptomator.domain.VaultPath
import org.cryptomator.domain.exception.CloudNodeAlreadyExistsException
import org.cryptomator.domain.exception.NetworkConnectionException
import org.cryptomator.domain.exception.NoSuchCloudFileException
import org.cryptomator.domain.repository.CloudContentRepository
import org.cryptomator.domain.repository.VaultDocumentChange
import org.cryptomator.domain.repository.VaultDocumentChangeResult
import org.cryptomator.domain.repository.VaultDocumentRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class VaultDocumentChangesTest {

	private val vault = Vault.aVault().withId(1L).withName("vault").withPath("/vault").withCloudType(CloudType.LOCAL).withPosition(0).build()
	private val cloudContentRepository: CloudContentRepository<Cloud, CloudNode, CloudFolder, CloudFile> = mock()
	private val vaultDocumentRepository: VaultDocumentRepository = mock()
	private val folder: CloudFolder = mock()
	private val file: CloudFile = mock { on { name } doAnswer { "x.txt" } }

	@Test
	fun `a rename whose name is taken in the cloud returns the renamed node`() {
		val renamed: CloudFile = mock()
		whenever(vaultDocumentRepository.pathOf(file)).thenReturn(VaultPath(vault, "x.txt"))
		whenever(vaultDocumentRepository.rename(VaultPath(vault, "x.txt"), "y.txt")).thenReturn(VaultDocumentChange<CloudNode> { VaultDocumentChangeResult(VaultPath(vault, "y (1A2B3).txt"), renamed) })

		val result = RenameFile(cloudContentRepository, vaultDocumentRepository, file, "y.txt").execute()

		assertSame(renamed, result.value())
		assertEquals("x.txt", result.oldName)
		verifyNoInteractions(cloudContentRepository)
	}

	@Test
	fun `nodes in a vault are moved and folders renamed through the vault's cache`() {
		val target: CloudFolder = mock()
		val movedFile: CloudFile = mock()
		val movedFolder: CloudFolder = mock()
		val renamedFolder: CloudFolder = mock()
		whenever(vaultDocumentRepository.pathOf(target)).thenReturn(VaultPath(vault, "b"))
		whenever(vaultDocumentRepository.pathOf(file)).thenReturn(VaultPath(vault, "x.txt"))
		whenever(vaultDocumentRepository.pathOf(folder)).thenReturn(VaultPath(vault, "a"))
		whenever(vaultDocumentRepository.move(VaultPath(vault, "x.txt"), VaultPath(vault, "b"))).thenReturn(VaultDocumentChange<CloudNode> { VaultDocumentChangeResult(VaultPath(vault, "b/x.txt"), movedFile) })
		whenever(vaultDocumentRepository.move(VaultPath(vault, "a"), VaultPath(vault, "b"))).thenReturn(VaultDocumentChange<CloudNode> { VaultDocumentChangeResult(VaultPath(vault, "b/a"), movedFolder) })
		whenever(vaultDocumentRepository.rename(VaultPath(vault, "a"), "c")).thenReturn(VaultDocumentChange<CloudNode> { VaultDocumentChangeResult(VaultPath(vault, "c"), renamedFolder) })

		assertEquals(listOf(movedFile), MoveFiles(cloudContentRepository, vaultDocumentRepository, listOf(file), target).execute())
		assertEquals(listOf(movedFolder), MoveFolders(cloudContentRepository, vaultDocumentRepository, listOf(folder), target).execute())
		assertSame(renamedFolder, RenameFolder(cloudContentRepository, vaultDocumentRepository, folder, "c").execute().value())
		verifyNoInteractions(cloudContentRepository)
	}

	@Test
	fun `a name taken in the vault fails the change`() {
		whenever(vaultDocumentRepository.pathOf(folder)).thenReturn(VaultPath(vault))
		whenever(vaultDocumentRepository.createFolder(VaultPath(vault), "new")).thenThrow(CloudNodeAlreadyExistsException("new"))

		assertThrows<CloudNodeAlreadyExistsException> { CreateFolder(cloudContentRepository, vaultDocumentRepository, folder, "new").execute() }
	}

	@Test
	fun `a change waits for the cloud and fails if the cloud rejects it`() {
		whenever(vaultDocumentRepository.pathOf(folder)).thenReturn(VaultPath(vault))
		whenever(vaultDocumentRepository.createFolder(VaultPath(vault), "new")).thenReturn(VaultDocumentChange<CloudFolder> { throw NetworkConnectionException() })

		assertThrows<NetworkConnectionException> { CreateFolder(cloudContentRepository, vaultDocumentRepository, folder, "new").execute() }
	}

	@Test
	fun `deleting a node that is gone counts as deleted`() {
		whenever(vaultDocumentRepository.pathOf(file)).thenReturn(VaultPath(vault, "x.txt"))
		whenever(vaultDocumentRepository.delete(VaultPath(vault, "x.txt"))).thenThrow(NoSuchCloudFileException("x.txt"))

		assertEquals(listOf(file), DeleteNodes(cloudContentRepository, vaultDocumentRepository, listOf<CloudNode>(file)).execute())
		verifyNoInteractions(cloudContentRepository)
	}
}
