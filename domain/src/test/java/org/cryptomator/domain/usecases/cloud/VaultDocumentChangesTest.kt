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
import org.cryptomator.domain.usecases.DownloadFile
import org.cryptomator.domain.usecases.ProgressAware
import java.io.OutputStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
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
		whenever(vaultDocumentRepository.rename(VaultPath(vault, "x.txt"), "y.txt", false)).thenReturn(change<CloudNode> { VaultDocumentChangeResult(VaultPath(vault, "y (1A2B3).txt"), renamed) })

		val result = RenameFile(cloudContentRepository, vaultDocumentRepository, file, "y.txt", null).execute()

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
		whenever(vaultDocumentRepository.move(VaultPath(vault, "x.txt"), VaultPath(vault, "b"), false)).thenReturn(change<CloudNode> { VaultDocumentChangeResult(VaultPath(vault, "b/x.txt"), movedFile) })
		whenever(vaultDocumentRepository.move(VaultPath(vault, "a"), VaultPath(vault, "b"), false)).thenReturn(change<CloudNode> { VaultDocumentChangeResult(VaultPath(vault, "b/a"), movedFolder) })
		whenever(vaultDocumentRepository.rename(VaultPath(vault, "a"), "c", false)).thenReturn(change<CloudNode> { VaultDocumentChangeResult(VaultPath(vault, "c"), renamedFolder) })

		assertEquals(listOf(movedFile), MoveFiles(cloudContentRepository, vaultDocumentRepository, listOf(file), target, null).execute())
		assertEquals(listOf(movedFolder), MoveFolders(cloudContentRepository, vaultDocumentRepository, listOf(folder), target, null).execute())
		assertSame(renamedFolder, RenameFolder(cloudContentRepository, vaultDocumentRepository, folder, "c", null).execute().value())
		verifyNoInteractions(cloudContentRepository)
	}

	@Test
	fun `a name taken in the vault fails the change`() {
		whenever(vaultDocumentRepository.pathOf(folder)).thenReturn(VaultPath(vault))
		whenever(vaultDocumentRepository.createFolder(VaultPath(vault), "new", false)).thenThrow(CloudNodeAlreadyExistsException("new"))

		assertThrows<CloudNodeAlreadyExistsException> { CreateFolder(cloudContentRepository, vaultDocumentRepository, folder, "new", null).execute() }
	}

	@Test
	fun `a change waits for the cloud and fails if the cloud rejects it`() {
		whenever(vaultDocumentRepository.pathOf(folder)).thenReturn(VaultPath(vault))
		whenever(vaultDocumentRepository.createFolder(VaultPath(vault), "new", false)).thenReturn(change<CloudFolder> { throw NetworkConnectionException() })

		assertThrows<NetworkConnectionException> { CreateFolder(cloudContentRepository, vaultDocumentRepository, folder, "new", null).execute() }
	}

	@Test
	fun `deleting a node that is gone counts as deleted`() {
		whenever(vaultDocumentRepository.pathOf(file)).thenReturn(VaultPath(vault, "x.txt"))
		whenever(vaultDocumentRepository.delete(VaultPath(vault, "x.txt"), false)).thenThrow(NoSuchCloudFileException("x.txt"))

		assertEquals(listOf(file), DeleteNodes(cloudContentRepository, vaultDocumentRepository, listOf<CloudNode>(file), null).execute())
		verifyNoInteractions(cloudContentRepository)
	}

	@Test
	fun `files in a vault are read from their local copies`() {
		val target: OutputStream = mock()
		val progressAware: ProgressAware<DownloadState> = mock()
		whenever(vaultDocumentRepository.pathOf(file)).thenReturn(VaultPath(vault, "x.txt"))

		val downloadFile = DownloadFile.Builder().setDownloadFile(file).setDataSink(target).build()
		assertEquals(listOf(file), DownloadFiles(cloudContentRepository, vaultDocumentRepository, listOf(downloadFile)).execute(progressAware))

		verify(vaultDocumentRepository).read(VaultPath(vault, "x.txt"), target, progressAware)
		verify(target).close()
		verifyNoInteractions(cloudContentRepository)
	}

	@Test
	fun `files are opened and external editing is started and ended by documentId`() {
		val progressAware: ProgressAware<DownloadState> = mock()
		whenever(vaultDocumentRepository.pathOf(file)).thenReturn(VaultPath(vault, "x.txt"))
		whenever(vaultDocumentRepository.open(VaultPath(vault, "x.txt"), progressAware)).thenReturn("1:2")

		assertEquals(listOf("1:2"), OpenVaultFiles(vaultDocumentRepository, listOf(file)).execute(progressAware))
		StartExternalEditing(vaultDocumentRepository, "1:2").execute()
		EndExternalEditing(vaultDocumentRepository, "1:2").execute()

		verify(vaultDocumentRepository).startExternalEditing("1:2")
		verify(vaultDocumentRepository).endExternalEditing("1:2")
	}

	@Test
	fun `an optimistic change returns the node as the cache has it without waiting for the cloud`() {
		val created: CloudFolder = mock()
		val renamedFile: CloudFile = mock()
		val renamedFolder: CloudFolder = mock()
		val movedFile: CloudFile = mock()
		val movedFolder: CloudFolder = mock()
		val target: CloudFolder = mock()
		whenever(vaultDocumentRepository.pathOf(folder)).thenReturn(VaultPath(vault, "a"))
		whenever(vaultDocumentRepository.pathOf(target)).thenReturn(VaultPath(vault, "b"))
		whenever(vaultDocumentRepository.pathOf(file)).thenReturn(VaultPath(vault, "x.txt"))
		whenever(vaultDocumentRepository.createFolder(VaultPath(vault, "a"), "new", true)).thenReturn(change(created) { throw NetworkConnectionException() })
		whenever(vaultDocumentRepository.rename(VaultPath(vault, "x.txt"), "y.txt", true)).thenReturn(change<CloudNode>(renamedFile) { throw NetworkConnectionException() })
		whenever(vaultDocumentRepository.rename(VaultPath(vault, "a"), "c", true)).thenReturn(change<CloudNode>(renamedFolder) { throw NetworkConnectionException() })
		whenever(vaultDocumentRepository.move(VaultPath(vault, "x.txt"), VaultPath(vault, "b"), true)).thenReturn(change<CloudNode>(movedFile) { throw NetworkConnectionException() })
		whenever(vaultDocumentRepository.move(VaultPath(vault, "a"), VaultPath(vault, "b"), true)).thenReturn(change<CloudNode>(movedFolder) { throw NetworkConnectionException() })
		whenever(vaultDocumentRepository.delete(VaultPath(vault, "x.txt"), true)).thenReturn(change<Unit> { throw NetworkConnectionException() })

		assertSame(created, CreateFolder(cloudContentRepository, vaultDocumentRepository, folder, "new", true).execute())
		assertSame(renamedFile, RenameFile(cloudContentRepository, vaultDocumentRepository, file, "y.txt", true).execute().value())
		assertSame(renamedFolder, RenameFolder(cloudContentRepository, vaultDocumentRepository, folder, "c", true).execute().value())
		assertEquals(listOf(movedFile), MoveFiles(cloudContentRepository, vaultDocumentRepository, listOf(file), target, true).execute())
		assertEquals(listOf(movedFolder), MoveFolders(cloudContentRepository, vaultDocumentRepository, listOf(folder), target, true).execute())
		assertEquals(listOf(file), DeleteNodes(cloudContentRepository, vaultDocumentRepository, listOf<CloudNode>(file), true).execute())
		verifyNoInteractions(cloudContentRepository)
	}

	@Test
	fun `an optimistic move of a node that is gone meanwhile has no result`() {
		val target: CloudFolder = mock()
		whenever(vaultDocumentRepository.pathOf(target)).thenReturn(VaultPath(vault, "b"))
		whenever(vaultDocumentRepository.pathOf(file)).thenReturn(VaultPath(vault, "x.txt"))
		whenever(vaultDocumentRepository.move(VaultPath(vault, "x.txt"), VaultPath(vault, "b"), true)).thenReturn(change<CloudNode> { throw NetworkConnectionException() })

		assertEquals(emptyList<CloudFile>(), MoveFiles(cloudContentRepository, vaultDocumentRepository, listOf(file), target, true).execute())
	}

	@Test
	fun `an upload the user retries reports its failure, a resume of all uploads doesn't`() {
		whenever(vaultDocumentRepository.pathOf(file)).thenReturn(VaultPath(vault, "x.txt"))

		ResumeVaultUploads(vaultDocumentRepository, vault, listOf(file)).execute()
		ResumeVaultUploads(vaultDocumentRepository, vault, null).execute()

		verify(vaultDocumentRepository).resumeUploads(vault, listOf(VaultPath(vault, "x.txt")), true)
		verify(vaultDocumentRepository).resumeUploads(vault, null, false)
	}

	private fun <T> change(cachedNode: T? = null, awaited: () -> VaultDocumentChangeResult<T>): VaultDocumentChange<T> {
		return object : VaultDocumentChange<T> {
			override fun cachedNode(): T? = cachedNode

			override fun await(): VaultDocumentChangeResult<T> = awaited()
		}
	}
}
