package org.cryptomator.domain.usecases.cloud

import org.cryptomator.domain.CachedVaultFile
import org.cryptomator.domain.CachedVaultFolder
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.CloudFolder
import org.cryptomator.domain.CloudNode
import org.cryptomator.domain.CloudType
import org.cryptomator.domain.Vault
import org.cryptomator.domain.VaultPath
import org.cryptomator.domain.exception.NetworkConnectionException
import org.cryptomator.domain.repository.CloudContentRepository
import org.cryptomator.domain.repository.VaultDocumentRepository
import org.cryptomator.domain.repository.VaultListing
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class GetCloudListRecursiveTest {

	private val vault = Vault.aVault().withId(1L).withName("vault").withPath("/vault").withCloudType(CloudType.LOCAL).withPosition(0).build()
	private val cloudContentRepository: CloudContentRepository<Cloud, CloudNode, CloudFolder, CloudFile> = mock()
	private val vaultDocumentRepository: VaultDocumentRepository = mock()

	@Test
	fun `a vault folder is listed through the vault's cache, so that pending uploads are included`() {
		val folder = CachedVaultFolder(null, "/a")
		val subfolder = CachedVaultFolder(null, "/a/b")
		val pending = CachedVaultFile(folder, "pending.txt", 3, null, CachedVaultFile.Upload.PENDING, null)
		val uploaded = CachedVaultFile(subfolder, "uploaded.txt", 5, null, CachedVaultFile.Upload.NONE, null)
		whenever(vaultDocumentRepository.pathOf(folder)).thenReturn(VaultPath(vault, "a"))
		whenever(vaultDocumentRepository.pathOf(subfolder)).thenReturn(VaultPath(vault, "a/b"))
		whenever(vaultDocumentRepository.list(folder, true)).thenReturn(VaultListing(folder, listOf(subfolder, pending), false, null))
		whenever(vaultDocumentRepository.list(subfolder, true)).thenReturn(VaultListing(subfolder, listOf(uploaded), false, null))

		val listing = GetCloudListRecursive(cloudContentRepository, vaultDocumentRepository, listOf(folder)).execute()

		val content = listing.foldersContent.single()
		assertSame(folder, content.parent)
		assertEquals(listOf<CloudFile>(pending), content.files)
		assertSame(subfolder, content.folders.single().parent)
		assertEquals(listOf<CloudFile>(uploaded), content.folders.single().files)
		verifyNoInteractions(cloudContentRepository)
	}

	@Test
	fun `a folder outside a vault is listed from the cloud`() {
		val folder: CloudFolder = mock()
		val file: CloudFile = mock()
		whenever(cloudContentRepository.list(folder)).thenReturn(listOf(file))

		val listing = GetCloudListRecursive(cloudContentRepository, vaultDocumentRepository, listOf(folder)).execute()

		assertEquals(listOf(file), listing.foldersContent.single().files)
		verify(vaultDocumentRepository, never()).list(any(), any())
	}

	@Test
	fun `a vault folder that can't be listed fails the listing`() {
		val folder = CachedVaultFolder(null, "/a")
		whenever(vaultDocumentRepository.pathOf(folder)).thenReturn(VaultPath(vault, "a"))
		whenever(vaultDocumentRepository.list(folder, true)).thenThrow(NetworkConnectionException())

		assertThrows<NetworkConnectionException> { GetCloudListRecursive(cloudContentRepository, vaultDocumentRepository, listOf(folder)).execute() }
	}
}
