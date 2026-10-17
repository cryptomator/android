package org.cryptomator.data.documents

import org.cryptomator.data.db.dao.CachedFileDao
import org.cryptomator.data.db.dao.DocumentMetadataDao
import org.cryptomator.data.db.entities.CachedFileEntity
import org.cryptomator.data.db.entities.DocumentMetadataEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

class ThumbnailStoreTest {

	@TempDir
	lateinit var directory: File

	private val metadataDao: DocumentMetadataDao = mock()
	private val cachedFileDao: CachedFileDao = mock()

	private val thumbnails: File
		get() = File(directory, "thumbnails")

	private val copies: File
		get() = File(directory, "copies").apply { mkdirs() }

	@Test
	fun `a file changed in the cloud gets a new thumbnail`() {
		val store = store()
		store.put(synced(lastModified = 5_000), requireNotNull(store.version(synced(lastModified = 5_000))), JPEG)

		assertNotNull(store.thumbnail(synced(lastModified = 5_400)))
		assertNull(store.thumbnail(synced(lastModified = 9_000)))
	}

	@Test
	fun `each local save of a file whose upload waits gets a new thumbnail, also if it goes to the same copy`() {
		val copy = File(copies, "copy").apply { writeText("first") }
		copy.setLastModified(1_000_000)
		whenever(cachedFileDao.load(VAULT_ID, "x.jpg")).thenReturn(cachedFile("copy"))
		val store = store()
		val firstSave = requireNotNull(store.version(uploading()))
		store.put(uploading(), firstSave, JPEG)

		copy.setLastModified(2_000_000)

		assertNotEquals(firstSave, store.version(uploading()))
		assertNull(store.thumbnail(uploading()))
	}

	@Test
	fun `a save after an upload and a listing gets a new thumbnail`() {
		val copy = File(copies, "copy").apply { writeText("uploaded") }
		copy.setLastModified(1_000_000)
		whenever(cachedFileDao.load(VAULT_ID, "x.jpg")).thenReturn(cachedFile("copy"))
		val store = store()
		val uploaded = requireNotNull(store.version(synced(lastModified = 1_000_000)))
		store.put(synced(lastModified = 1_000_000), uploaded, JPEG)

		copy.setLastModified(2_000_000)

		assertNull(store.thumbnail(uploading()))
	}

	@Test
	fun `thumbnails of nodes that are gone or changed are deleted, those of nodes being deleted are kept`() {
		val store = store()
		val current = synced(lastModified = 5_000)
		store.put(current, requireNotNull(store.version(current)), JPEG)
		val beingDeleted = synced(lastModified = 5_000, id = 8).apply { pendingDelete = 1 }
		store.put(beingDeleted, requireNotNull(store.version(beingDeleted)), JPEG)
		val changed = synced(lastModified = 5_000, id = 9)
		store.put(changed, requireNotNull(store.version(changed)), JPEG)
		val gone = synced(lastModified = 5_000, id = 10)
		store.put(gone, requireNotNull(store.version(gone)), JPEG)
		whenever(metadataDao.loadById(7)).thenReturn(current)
		whenever(metadataDao.loadById(8)).thenReturn(beingDeleted)
		whenever(metadataDao.loadById(9)).thenReturn(synced(lastModified = 9_000, id = 9))

		store.evict()

		assertEquals(setOf("1_7_m5.jpg", "1_8_m5.jpg"), thumbnails.list()!!.toSet())
	}

	@Test
	fun `current thumbnails are kept however old and many they are`() {
		val store = store()
		(7L..9L).forEach { id ->
			val metadata = synced(lastModified = 5_000, id = id)
			whenever(metadataDao.loadById(id)).thenReturn(metadata)
			store.put(metadata, requireNotNull(store.version(metadata)), JPEG)
			File(thumbnails, "1_${id}_m5.jpg").setLastModified(id * 1_000)
		}

		store.evict()

		assertEquals(setOf("1_7_m5.jpg", "1_8_m5.jpg", "1_9_m5.jpg"), thumbnails.list()!!.toSet())
	}

	@Test
	fun `the thumbnails of deleted nodes are deleted, those of other nodes are kept`() {
		val store = store()
		listOf(7L, 70L, 8L).forEach { id -> synced(lastModified = 5_000, id = id).let { store.put(it, requireNotNull(store.version(it)), JPEG) } }

		store.delete(listOf("1:7"))

		assertEquals(setOf("1_70_m5.jpg", "1_8_m5.jpg"), thumbnails.list()!!.toSet())
	}

	@Test
	fun `clearing deletes all thumbnails, which then take no space`() {
		val store = store()
		listOf(7L, 8L).forEach { id -> synced(lastModified = 5_000, id = id).let { store.put(it, requireNotNull(store.version(it)), JPEG) } }
		assertEquals(JPEG.size * 2L, store.size())

		store.clear()

		assertEquals(0L, store.size())
		assertEquals(emptyList<String>(), thumbnails.list()!!.toList())
	}

	private fun store(): ThumbnailStore {
		return ThumbnailStore(thumbnails, metadataDao, cachedFileDao, copies)
	}

	private fun synced(lastModified: Long, id: Long = 7): DocumentMetadataEntity {
		return entity(id).apply { this.lastModified = lastModified }
	}

	private fun uploading(): DocumentMetadataEntity {
		return entity(7).apply {
			lastModified = 1_000_000
			status = DocumentMetadataEntity.STATUS_UPLOADING
		}
	}

	private fun entity(id: Long): DocumentMetadataEntity {
		return DocumentMetadataEntity().apply {
			this.id = id
			vaultId = VAULT_ID
			path = "x.jpg"
			parentPath = ""
			name = "x.jpg"
			mimeType = "image/jpeg"
		}
	}

	private fun cachedFile(localName: String): CachedFileEntity {
		return CachedFileEntity().apply {
			vaultId = VAULT_ID
			path = "x.jpg"
			this.localName = localName
		}
	}

	companion object {

		private const val VAULT_ID = 1L
		private val JPEG = byteArrayOf(1, 2, 3)
	}
}
