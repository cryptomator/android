package org.cryptomator.data.documents

import org.cryptomator.data.db.dao.CachedFileDao
import org.cryptomator.data.db.dao.DocumentMetadataDao
import org.cryptomator.data.db.entities.DocumentMetadataEntity
import android.provider.DocumentsContract
import org.cryptomator.domain.CloudType
import org.cryptomator.domain.Vault
import org.cryptomator.domain.VaultPath
import org.cryptomator.domain.exception.NetworkConnectionException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File

class ThumbnailGeneratorTest {

	@TempDir
	lateinit var directory: File

	private val vault = Vault.aVault().withId(1L).withName("vault").withPath("/vault").withCloudType(CloudType.LOCAL).withPosition(0).withUnlocked(true).build()
	private val metadataCache: DocumentMetadataCache = mock()
	private val contentCache: DocumentContentCache = mock()
	private val events = VaultDocumentEvents()
	private val pending = ArrayDeque<Runnable>()
	private val made = mutableListOf<String>()

	@Test
	fun `the thumbnail asked for last is made first`() {
		listOf("a.jpg", "b.jpg", "c.jpg").forEachIndexed { index, name -> givenImage(name, id = index + 1L) }
		val inTest = generator { image -> made.add(image.readText()); JPEG }

		inTest.request(listOf(path("a.jpg")))
		inTest.request(listOf(path("b.jpg")))
		inTest.request(listOf(path("c.jpg"), path("a.jpg")))
		runPending()

		assertEquals(listOf("c.jpg", "a.jpg", "b.jpg"), made)
	}

	@Test
	fun `an image that can't be decoded isn't tried again until it changes`() {
		givenImage("a.jpg", id = 1)
		val changed = mutableListOf<Folder>()
		events.addListener { changed.add(Folder(it)) }
		val inTest = generator { image -> made.add(image.readText()); null }

		inTest.request(listOf(path("a.jpg")))
		runPending()
		inTest.request(listOf(path("a.jpg")))
		runPending()
		assertEquals(listOf("a.jpg"), made)

		givenImage("a.jpg", id = 1, lastModified = 9_000)
		inTest.request(listOf(path("a.jpg")))
		runPending()
		assertEquals(listOf("a.jpg", "a.jpg"), made)
		assertEquals(emptyList<Folder>(), changed)
	}

	@Test
	fun `a thumbnail that is done is stored and its folder is told`() {
		val metadata = givenImage("a.jpg", id = 1)
		val changed = mutableListOf<VaultPath>()
		events.addListener { changed.add(it) }
		val store = store(mock { on { loadById(1) } doReturn metadata })

		generator(store) { JPEG }.request(listOf(path("a.jpg")))
		runPending()

		assertNotNull(store.thumbnail(metadata))
		assertEquals(listOf(VaultPath(vault)), changed)
	}

	@Test
	fun `an image is read from its current copy, which is released also if it can't be read`() {
		givenImage("a.jpg", id = 1)
		givenImage("b.jpg", id = 2)
		val copyA = File(directory, "copy-a.jpg")
		val inTest = generator { image -> if (image == copyA) throw IllegalStateException("broken") else JPEG }

		inTest.request(listOf(path("a.jpg"), path("b.jpg")))
		runPending()

		verify(contentCache).release(copyA)
		verify(contentCache).release(File(directory, "copy-b.jpg"))
		verify(contentCache, never()).downloadUnrecorded(any(), any())
	}

	@Test
	fun `an image without a current copy is downloaded to a temporary file, which is deleted afterwards, also if the download fails`() {
		givenImage("a.jpg", id = 1, copy = false)
		givenImage("b.jpg", id = 2, copy = false)
		val downloaded = mutableListOf<File>()
		doAnswer { invocation ->
			val target = invocation.getArgument<File>(1)
			downloaded.add(target)
			if (invocation.getArgument<VaultPath>(0) == path("a.jpg")) throw NetworkConnectionException()
			target.writeText("downloaded")
		}.whenever(contentCache).downloadUnrecorded(any(), any())
		val inTest = generator { image -> made.add(image.readText()); JPEG }

		inTest.request(listOf(path("a.jpg"), path("b.jpg")))
		runPending()

		assertEquals(listOf("downloaded"), made)
		assertEquals(2, downloaded.size)
		downloaded.forEach { assertFalse(it.exists()) }
		verify(contentCache, never()).release(any())
	}

	@Test
	fun `folders, other files and images that have their thumbnail are skipped`() {
		val store = store()
		val done = givenImage("a.jpg", id = 1)
		store.put(done, requireNotNull(store.version(done)), JPEG)
		givenImage("b.txt", id = 2, mimeType = "text/plain")
		givenImage("c", id = 3, mimeType = DocumentsContract.Document.MIME_TYPE_DIR)
		val inTest = generator(store) { image -> made.add(image.readText()); JPEG }

		inTest.request(listOf(path("a.jpg"), path("b.txt"), path("c")))
		runPending()

		assertEquals(emptyList<String>(), made)
	}

	@Test
	fun `the outdated thumbnails are deleted once the thumbnails asked for are made`() {
		givenImage("a.jpg", id = 1)
		val store: ThumbnailStore = mock { on { version(any()) } doAnswer { "v" } }

		generator(store) { null }.request(listOf(path("a.jpg")))
		runPending()
		verify(store, never()).evict()

		givenImage("b.jpg", id = 2)
		generator(store) { JPEG }.request(listOf(path("b.jpg")))
		runPending()
		verify(store).evict()
	}

	private data class Folder(val path: VaultPath)

	private fun givenImage(name: String, id: Long, lastModified: Long = 5_000, mimeType: String = "image/jpeg", copy: Boolean = true): DocumentMetadataEntity {
		val metadata = DocumentMetadataEntity().apply {
			this.id = id
			vaultId = 1L
			path = name
			parentPath = ""
			this.name = name
			this.mimeType = mimeType
			this.lastModified = lastModified
		}
		whenever(metadataCache.cachedDocument(path(name))).thenReturn(metadata)
		whenever(contentCache.currentCopy(eq(path(name)), any())).thenReturn(if (copy) File(directory, "copy-$name").apply { writeText(name) } else null)
		return metadata
	}

	private fun store(metadataDao: DocumentMetadataDao = mock()): ThumbnailStore {
		return ThumbnailStore(File(directory, "thumbnails"), metadataDao, mock<CachedFileDao>(), directory)
	}

	private fun generator(store: ThumbnailStore = store(), makeThumbnail: (File) -> ByteArray?): ThumbnailGenerator {
		return ThumbnailGenerator(store, metadataCache, contentCache, events, File(directory, "tmp"), { pending.addLast(it) }, makeThumbnail)
	}

	private fun runPending() {
		while (pending.isNotEmpty()) {
			pending.removeFirst().run()
		}
	}

	private fun path(path: String): VaultPath {
		return VaultPath(vault, path)
	}

	companion object {

		private val JPEG = byteArrayOf(1, 2, 3)
	}
}
