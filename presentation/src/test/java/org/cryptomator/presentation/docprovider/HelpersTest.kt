package org.cryptomator.presentation.docprovider

import org.cryptomator.data.db.entities.DocumentMetadataEntity
import org.cryptomator.data.documents.DocumentContentCache
import org.cryptomator.data.documents.ThumbnailGenerator
import org.cryptomator.data.documents.ThumbnailStore
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudType
import org.cryptomator.domain.Vault
import org.cryptomator.domain.VaultPath
import org.cryptomator.domain.exception.MissingCryptorException
import org.cryptomator.domain.exception.NoSuchCloudFileException
import org.cryptomator.presentation.licensing.LicenseEnforcer
import org.cryptomator.util.FlavorConfig
import org.cryptomator.util.SharedPreferencesHandler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.io.File
import java.io.FileNotFoundException

class HelpersTest {

	private val contentCache: DocumentContentCache = mock()
	private val thumbnailStore: ThumbnailStore = mock()
	private val thumbnailGenerator: ThumbnailGenerator = mock()
	private val sharedPreferencesHandler: SharedPreferencesHandler = mock()
	private val licenseEnforcer = LicenseEnforcer(sharedPreferencesHandler)
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

	@Test
	fun `a document moved while its path was used is used again where it is then, as often as it moved`() {
		val paths = ArrayDeque(listOf(path("a/x.jpg"), path("a (1A2B3)/x.jpg"), path("b/x.jpg")))
		val used = mutableListOf<VaultPath>()

		val result = followingDocument("1:7", { paths.removeFirst() }) { path ->
			used.add(path)
			when (path.path) {
				"a/x.jpg" -> throw FileNotFoundException("Unknown document: 1:?")
				"a (1A2B3)/x.jpg" -> throw NoSuchCloudFileException("1:7")
				else -> "opened"
			}
		}

		assertEquals("opened", result)
		assertEquals(listOf(path("a/x.jpg"), path("a (1A2B3)/x.jpg"), path("b/x.jpg")), used)
	}

	@Test
	fun `a document that didn't move, or is gone, keeps its failure, and other failures aren't retried`() {
		val notFound = FileNotFoundException("Unknown document: 1:7")
		var calls = 0

		assertSame(notFound, assertThrows<FileNotFoundException> { followingDocument("1:7", { path("a/x.jpg") }) { check(++calls < 3) { "retried" }; throw notFound } })
		assertEquals(1, calls)
		val resolved = ArrayDeque(listOf(path("a/x.jpg")))
		assertSame(notFound, assertThrows<FileNotFoundException> { followingDocument("1:7", { resolved.removeFirstOrNull() ?: throw FileNotFoundException("gone") }) { calls++; throw notFound } })
		assertEquals(2, calls)
		val moved = ArrayDeque(listOf(path("a/x.jpg"), path("b/x.jpg")))
		assertThrows<IllegalStateException> { followingDocument("1:7", { moved.removeFirst() }) { calls++; throw IllegalStateException() } }
		assertEquals(3, calls)
	}

	@Test
	fun `a vault is writable with a license of the app`() {
		givenLicense(token = "token")
		val path = vaultPath()

		assertTrue(isWritable(path.vault, licenseEnforcer, locked))
		assertSame(path, requireWritable(path, licenseEnforcer, locked))
	}

	@Test
	fun `a vault is read-only once the trial expired without a license, every change is refused, and the app still tells the user`() {
		assumeTrue(!FlavorConfig.isPremiumFlavor, "Licensing logic is bypassed on this flavor")
		givenLicense(trialExpiration = System.currentTimeMillis() - 1000)
		val path = vaultPath()

		assertFalse(isWritable(path.vault, licenseEnforcer, locked))
		assertThrows<UnsupportedOperationException> { requireWritable(path, licenseEnforcer, locked) }
		verify(sharedPreferencesHandler, never()).setTrialExpired(any())
	}

	@Test
	fun `a vault is writable during the trial`() {
		givenLicense(trialExpiration = System.currentTimeMillis() + 60_000)

		assertTrue(isWritable(vaultPath().vault, licenseEnforcer, locked))
	}

	@Test
	fun `a Hub vault is writable with the paid Hub license its unlock found, without one only with a license of the app`() {
		assumeTrue(!FlavorConfig.isPremiumFlavor, "Licensing logic is bypassed on this flavor")
		givenLicense()
		// like a vault the repository loads, which doesn't know about Hub
		val stored = vaultPath().vault
		val paid = Vault.aCopyOf(stored).withHubVault(true).withHubPaidLicense(true).build()
		val unpaid = Vault.aCopyOf(stored).withHubVault(true).withHubPaidLicense(false).build()

		assertTrue(isWritable(stored, licenseEnforcer) { paid })
		assertFalse(isWritable(stored, licenseEnforcer) { unpaid })
		assertFalse(isWritable(stored, licenseEnforcer, locked))
		givenLicense(token = "token")
		assertTrue(isWritable(stored, licenseEnforcer) { unpaid })
	}

	@Test
	fun `a read-only cloud keeps the vault read-only, also with a license`() {
		givenLicense(token = "token")

		assertFalse(isWritable(vaultPath(readOnlyCloud = true).vault, licenseEnforcer, locked))
	}

	private val locked: (Vault) -> Vault? = { null }

	private fun givenLicense(token: String = "", trialExpiration: Long = 0) {
		whenever(sharedPreferencesHandler.licenseToken()).thenReturn(token)
		whenever(sharedPreferencesHandler.hasRunningSubscription()).thenReturn(false)
		whenever(sharedPreferencesHandler.trialExpirationDate()).thenReturn(trialExpiration)
		whenever(sharedPreferencesHandler.isTrialExpired()).thenReturn(false)
	}

	private fun vaultPath(readOnlyCloud: Boolean = false): VaultPath {
		val cloud: Cloud = mock {
			on { type() } doReturn CloudType.LOCAL
			on { isReadOnly() } doReturn readOnlyCloud
		}
		return VaultPath(Vault.aVault().withId(1L).withName("vault").withPath("/vault").withCloud(cloud).withPosition(0).withUnlocked(true).build(), "x.txt")
	}

	private fun path(path: String): VaultPath {
		return VaultPath(Vault.aVault().withId(1L).withName("vault").withPath("/vault").withCloudType(CloudType.LOCAL).withPosition(0).withUnlocked(true).build(), path)
	}

	private fun path(unlocked: Boolean): VaultPath {
		return VaultPath(Vault.aVault().withId(1L).withName("vault").withPath("/vault").withCloudType(CloudType.LOCAL).withPosition(0).withUnlocked(unlocked).build(), "x.jpg")
	}
}
