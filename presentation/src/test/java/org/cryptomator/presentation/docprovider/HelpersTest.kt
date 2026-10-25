package org.cryptomator.presentation.docprovider

import org.cryptomator.data.db.entities.DocumentMetadataEntity
import org.cryptomator.data.documents.DocumentContentCache
import org.cryptomator.data.documents.ThumbnailGenerator
import org.cryptomator.data.documents.ThumbnailStore
import org.cryptomator.domain.CloudType
import org.cryptomator.domain.Vault
import org.cryptomator.domain.VaultPath
import org.cryptomator.domain.exception.MissingCryptorException
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.io.File
import java.io.FileNotFoundException

class HelpersTest {

	private val contentCache: DocumentContentCache = mock()
	private val thumbnailStore: ThumbnailStore = mock()
	private val thumbnailGenerator: ThumbnailGenerator = mock()
	private val metadata = DocumentMetadataEntity().apply {
		id = 7
		vaultId = 1
	}

	@Test
	fun `a document of an unlocked vault is opened as usual`() {
		assertNull(claimPinnedFileIfLocked(path(unlocked = true), contentCache) { IllegalStateException("locked") })
		verifyNoInteractions(contentCache)
	}

	@Test
	fun `a document of a locked vault is served from the copy an external editor has pinned`() {
		val pinned = File("pinned")
		whenever(contentCache.pinnedFile(path(unlocked = false))).thenReturn(pinned)

		assertSame(pinned, claimPinnedFileIfLocked(path(unlocked = false), contentCache) { IllegalStateException("locked") })
	}

	@Test
	fun `any other document of a locked vault requires an unlock`() {
		assertThrows<IllegalStateException> { claimPinnedFileIfLocked(path(unlocked = false), contentCache) { IllegalStateException("locked") } }
	}

	@Test
	fun `a request of a vault that is locked meanwhile requires an unlock`() {
		val authenticationRequired = IllegalStateException("locked")

		val thrown = assertThrows<IllegalStateException> { whileUnlocked(path(unlocked = true).vault, { authenticationRequired }) { throw MissingCryptorException() } }

		assertSame(authenticationRequired, thrown)
	}

	@Test
	fun `a request of an unlocked vault keeps its outcome`() {
		assertSame(metadata, whileUnlocked(path(unlocked = true).vault, { IllegalStateException("locked") }) { metadata })
		assertThrows<FileNotFoundException> { whileUnlocked(path(unlocked = true).vault, { IllegalStateException("locked") }) { throw FileNotFoundException() } }
	}

	@Test
	fun `a stored thumbnail is returned right away`() {
		val thumbnail = File("thumbnail.jpg")
		whenever(thumbnailStore.thumbnail(metadata)).thenReturn(thumbnail)

		assertSame(thumbnail, storedThumbnail(path(unlocked = true), metadata, thumbnailStore, thumbnailGenerator))
		verifyNoInteractions(thumbnailGenerator)
	}

	@Test
	fun `a missing thumbnail is asked for without waiting for it`() {
		assertThrows<FileNotFoundException> { storedThumbnail(path(unlocked = true), metadata, thumbnailStore, thumbnailGenerator) }

		verify(thumbnailGenerator).request(listOf(path(unlocked = true)))
	}

	private fun path(unlocked: Boolean): VaultPath {
		return VaultPath(Vault.aVault().withId(1L).withName("vault").withPath("/vault").withCloudType(CloudType.LOCAL).withPosition(0).withUnlocked(unlocked).build(), "x.jpg")
	}
}
