package org.cryptomator.presentation.docprovider

import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import org.cryptomator.data.cloud.crypto.CryptoCloud
import org.cryptomator.data.cloud.crypto.RootCryptoFolder
import org.cryptomator.data.db.dao.CachedFileDao
import org.cryptomator.data.db.entities.CachedFileEntity
import org.cryptomator.data.db.entities.DocumentMetadataEntity
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.CloudFolder
import org.cryptomator.domain.CloudNode
import org.cryptomator.domain.CloudType
import org.cryptomator.domain.Vault
import org.cryptomator.domain.repository.CloudContentRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class DocumentContentCacheTest {

	@TempDir
	lateinit var directory: File

	private val vault = Vault.aVault().withId(VAULT_ID).withName("vault").withPath("/vault").withCloudType(CloudType.LOCAL).withPosition(0).withUnlocked(true).build()
	private val cryptoCloud: CryptoCloud = mock { on { type() } doReturn CloudType.CRYPTO }
	private val root = RootCryptoFolder(cryptoCloud)
	private val repository: CloudContentRepository<Cloud, CloudNode, CloudFolder, CloudFile> = mock()
	private val dao: CachedFileDao = mock()
	private val metadataCache: DocumentMetadataCache = mock()
	private val now = TimeUnit.DAYS.toMillis(100)
	private lateinit var helpers: HelpersMock

	@BeforeEach
	fun setup() {
		helpers = HelpersMock(repository, cryptoCloud, root, listOf(vault))
	}

	@AfterEach
	fun tearDown() {
		helpers.close()
	}

	@Test
	fun `a copy is used while the file has not changed in the cloud`() {
		val copy = copy("copy", "local content")
		whenever(dao.loadLocalNames()).thenReturn(listOf("copy"))
		givenDocument(lastModified = 5_400)
		whenever(dao.load(VAULT_ID, "x.txt")).thenReturn(cachedFile("copy", lastModified = 5_000))

		val file = cache().file(vaultPath("x.txt"))

		assertEquals(copy, file)
		verify(repository, never()).read(any(), anyOrNull(), any(), any())
	}

	@Test
	fun `a copy with a pending upload is used even though the file changed in the cloud`() {
		val copy = copy("copy", "local changes")
		whenever(dao.loadLocalNames()).thenReturn(listOf("copy"))
		givenDocument(lastModified = 9_000, status = DocumentMetadataEntity.STATUS_UPLOADING)
		whenever(dao.load(VAULT_ID, "x.txt")).thenReturn(cachedFile("copy", lastModified = 5_000))

		assertEquals(copy, cache().file(vaultPath("x.txt")))
	}

	@Test
	fun `a file that changed in the cloud is downloaded again and replaces the copy`() {
		val copy = copy("copy", "old content")
		whenever(dao.loadLocalNames()).thenReturn(listOf("copy"))
		givenDocument(lastModified = 9_000)
		whenever(dao.load(VAULT_ID, "x.txt")).thenReturn(cachedFile("copy", lastModified = 5_000))
		givenCloudContent("new content")

		val file = cache().file(vaultPath("x.txt"))

		assertNotEquals(copy, file)
		assertEquals("new content", file.readText())
		assertFalse(copy.exists())
		verify(dao).store(argThat { path == "x.txt" && localName == file.name && lastModified == 9_000L })
	}

	@Test
	fun `copies without metadata are deleted`() {
		val orphan = copy("orphan", "")
		val kept = copy("kept", "")
		whenever(dao.loadLocalNames()).thenReturn(listOf("kept"))

		cache()

		assertFalse(orphan.exists())
		assertTrue(kept.exists())
	}

	@Test
	fun `old copies that can be downloaded again are deleted`() {
		val old = copy("old", "", lastModified = now - TimeUnit.HOURS.toMillis(6))
		val recent = copy("recent", "", lastModified = now - TimeUnit.HOURS.toMillis(1))
		val pending = copy("pending", "", lastModified = now - TimeUnit.HOURS.toMillis(6))
		whenever(dao.loadLocalNames()).thenReturn(listOf("old", "recent", "pending"))
		whenever(dao.loadEvictable()).thenReturn(listOf(cachedFile("old", path = "old.txt"), cachedFile("recent", path = "recent.txt")))

		cache()

		assertFalse(old.exists())
		verify(dao).delete(VAULT_ID, "old.txt")
		assertTrue(recent.exists())
		assertTrue(pending.exists())
	}

	@Test
	fun `a copy open for writing is not deleted`() {
		givenDocument(lastModified = 5_000, status = DocumentMetadataEntity.STATUS_CREATING)
		val cache = cache()
		val copy = cache.fileForWriting(vaultPath("x.txt"), keepContent = true)
		copy.setLastModified(now - TimeUnit.HOURS.toMillis(6))
		whenever(dao.loadEvictable()).thenReturn(listOf(cachedFile(copy.name)))
		givenDocument(lastModified = 9_000, path = "y.txt")
		givenCloudContent("other content")

		cache.file(vaultPath("y.txt"))

		assertTrue(copy.exists())
		cache.release(copy)
		cache.file(vaultPath("y.txt"))
		assertFalse(copy.exists())
	}

	@Test
	fun `a copy that is being downloaded is not deleted`() {
		givenDocument(lastModified = 9_000)
		val cache = cache()
		val handle: CloudFile = mock()
		val stored: CloudFile = mock()
		whenever(repository.file(eq(root), any())).thenReturn(handle)
		whenever(repository.metadata(handle)).thenReturn(stored)
		whenever(repository.read(eq(stored), anyOrNull(), any(), any())).thenAnswer {
			cache.evictAll()
			it.getArgument<OutputStream>(2).write("content".toByteArray())
		}

		val file = cache.file(vaultPath("x.txt"))

		assertEquals("content", file.readText())
	}

	@Test
	fun `a copy is kept until it is released`() {
		val copy = copy("copy", "content")
		givenDocument(lastModified = 5_000)
		whenever(dao.loadLocalNames()).thenReturn(listOf("copy"))
		whenever(dao.load(VAULT_ID, "x.txt")).thenReturn(cachedFile("copy", lastModified = 5_000))
		whenever(dao.loadEvictable()).thenReturn(listOf(cachedFile("copy")))
		val cache = cache()

		val file = cache.file(vaultPath("x.txt"))
		cache.evictAll()

		assertTrue(file.exists())
		cache.release(file)
		cache.evictAll()
		assertFalse(copy.exists())
	}

	@Test
	fun `an upload to the same file records the uploaded version as the copy's base`() {
		val copy = copy("copy", "local changes")
		val onUploaded = captureUploadCallback(copy)

		onUploaded(vaultPath("x.txt"), vaultPath("x.txt"), 9_000L)

		verify(dao).updateLastModified(VAULT_ID, "x.txt", 9_000L)
		verify(dao, never()).delete(any(), any())
	}

	@Test
	fun `an upload as a conflict copy makes the copy the conflict copy's local copy`() {
		val copy = copy("copy", "local changes")
		val onUploaded = captureUploadCallback(copy)

		onUploaded(vaultPath("x.txt"), vaultPath("x (1A2B3).txt"), 9_000L)

		verify(dao).delete(VAULT_ID, "x.txt")
		verify(dao).store(argThat { path == "x (1A2B3).txt" && localName == "copy" && lastModified == 9_000L })
	}

	@Test
	fun `pending uploads are resumed once per vault`() {
		val copy = copy("copy", "local changes")
		whenever(dao.loadLocalNames()).thenReturn(listOf("copy"))
		whenever(dao.load(VAULT_ID, "x.txt")).thenReturn(cachedFile("copy"))
		val pendingUploads = listOf(vaultPath("x.txt"))
		whenever(metadataCache.pendingUploads(vault)).thenReturn(pendingUploads)
		val cache = cache()

		cache.resumeUploads(vault)
		cache.resumeUploads(vault)

		verify(metadataCache).upload(eq(vaultPath("x.txt")), eq(copy), any(), any())
	}

	@Test
	fun `a copy written with an error is dropped together with its pending upload`() {
		val copy = copy("copy", "partly written")
		whenever(dao.loadLocalNames()).thenReturn(listOf("copy"))
		whenever(dao.load(VAULT_ID, "x.txt")).thenReturn(cachedFile("copy"))

		cache().discard(vaultPath("x.txt"))

		assertFalse(copy.exists())
		verify(dao).delete(VAULT_ID, "x.txt")
		verify(metadataCache).abandonUpload(vaultPath("x.txt"))
	}

	@Test
	fun `a copy written from scratch is based on the file's current version in the cloud`() {
		givenDocument(lastModified = 7_000)

		val copy = cache().fileForWriting(vaultPath("x.txt"), keepContent = false)

		verify(dao).store(argThat { path == "x.txt" && localName == copy.name && lastModified == 7_000L })
	}

	@Test
	fun `the oldest copies are deleted once the copies don't fit into the cache`() {
		val older = copy("older", "", lastModified = now - 2_000)
		val newer = copy("newer", "", lastModified = now - 1_000)
		RandomAccessFile(older, "rw").use { it.setLength(300L * 1024 * 1024) }
		RandomAccessFile(newer, "rw").use { it.setLength(300L * 1024 * 1024) }
		older.setLastModified(now - 2_000)
		newer.setLastModified(now - 1_000)
		whenever(dao.loadLocalNames()).thenReturn(listOf("older", "newer"))
		whenever(dao.loadEvictable()).thenReturn(listOf(cachedFile("older", path = "older.txt"), cachedFile("newer", path = "newer.txt")))

		cache()

		assertFalse(older.exists())
		assertTrue(newer.exists())
		verify(dao).delete(VAULT_ID, "older.txt")
	}

	@Test
	@Timeout(10)
	fun `a canceled download keeps its copy and its place in the order of requests until it has stopped`() {
		givenDocument(lastModified = 9_000)
		val downloads = LinkedBlockingQueue<Runnable>()
		val scheduler = CloudTaskScheduler({ it.run() }, { it.run() }, { downloads.put(it) })
		val cache = DocumentContentCache(dao, metadataCache, scheduler, directory, { _, _ -> AutoCloseable {} }) { now }
		val canceled = AtomicBoolean(false)
		val listening = CountDownLatch(1)
		var onCancel: CancellationSignal.OnCancelListener? = null
		val signal: CancellationSignal = mock()
		doAnswer {
			it.getArgument<CancellationSignal.OnCancelListener?>(0)?.let { listener ->
				onCancel = listener
				listening.countDown()
			}
		}.whenever(signal).setOnCancelListener(anyOrNull())
		doAnswer { if (canceled.get()) throw IllegalStateException("canceled") }.whenever(signal).throwIfCanceled()
		val opening = CompletableFuture.supplyAsync { runCatching { cache.file(vaultPath("x.txt"), signal) }.exceptionOrNull() }
		val laterChangeRan = CountDownLatch(1)
		var download: File? = null
		var downloadKeptWhileRunning = false
		var laterChangeRanWhileRunning = true
		var openingFailure: Throwable? = null
		val handle: CloudFile = mock()
		val stored: CloudFile = mock()
		whenever(repository.file(eq(root), any())).thenReturn(handle)
		whenever(repository.metadata(handle)).thenReturn(stored)
		whenever(repository.read(eq(stored), anyOrNull(), any(), any())).thenAnswer {
			listening.await(5, TimeUnit.SECONDS)
			canceled.set(true)
			onCancel!!.onCancel()
			openingFailure = opening.get(5, TimeUnit.SECONDS)
			cache.evictAll()
			scheduler.write(listOf(vaultPath("x.txt"))) { laterChangeRan.countDown() }
			download = directory.listFiles()!!.single()
			downloadKeptWhileRunning = download!!.exists()
			laterChangeRanWhileRunning = laterChangeRan.count == 0L
		}

		downloads.poll(5, TimeUnit.SECONDS)!!.run()

		assertInstanceOf(IllegalStateException::class.java, openingFailure)
		assertTrue(downloadKeptWhileRunning)
		assertFalse(laterChangeRanWhileRunning)
		assertEquals(0L, laterChangeRan.count)
		assertFalse(download!!.exists())
	}

	@Test
	fun `a copy that someone else still uses is kept after a failed write`() {
		val copy = copy("copy", "content")
		givenDocument(lastModified = 5_000)
		whenever(dao.loadLocalNames()).thenReturn(listOf("copy"))
		whenever(dao.load(VAULT_ID, "x.txt")).thenReturn(cachedFile("copy", lastModified = 5_000))
		val cache = cache()
		cache.file(vaultPath("x.txt"))
		val written = cache.fileForWriting(vaultPath("x.txt"), keepContent = true)

		cache.closedForWriting(written, IOException("writer died"), changed = true) { vaultPath("x.txt") }

		assertTrue(copy.exists())
		verify(dao, never()).delete(any(), any())
		verify(metadataCache, never()).abandonUpload(any())
	}

	@Test
	fun `a failed write drops the copy`() {
		val copy = copy("copy", "content")
		givenDocument(lastModified = 5_000)
		whenever(dao.loadLocalNames()).thenReturn(listOf("copy"))
		whenever(dao.load(VAULT_ID, "x.txt")).thenReturn(cachedFile("copy", lastModified = 5_000))
		val cache = cache()
		val written = cache.fileForWriting(vaultPath("x.txt"), keepContent = true)

		cache.closedForWriting(written, IOException("writer died"), changed = true) { vaultPath("x.txt") }

		assertFalse(copy.exists())
		verify(metadataCache).abandonUpload(vaultPath("x.txt"))
		verify(metadataCache, never()).upload(any(), any(), any(), any())
	}

	@Test
	fun `a changed copy is uploaded when it is closed, an unchanged one is not`() {
		givenDocument(lastModified = 5_000, status = DocumentMetadataEntity.STATUS_CREATING)
		val cache = cache()
		val unchanged = cache.fileForWriting(vaultPath("x.txt"), keepContent = true)
		cache.closedForWriting(unchanged, null, changed = false) { vaultPath("x.txt") }
		verify(metadataCache, never()).upload(any(), any(), any(), any())

		val changed = cache.fileForWriting(vaultPath("x.txt"), keepContent = true)
		cache.closedForWriting(changed, null, changed = true) { vaultPath("x.txt") }

		verify(metadataCache).upload(eq(vaultPath("x.txt")), eq(changed), any(), any())
		cache.evictAll()
		assertFalse(changed.exists())
	}

	@Test
	fun `a copy whose file descriptor was detached is uploaded once its writer closes it`() {
		givenDocument(lastModified = 5_000, status = DocumentMetadataEntity.STATUS_CREATING)
		val closeWatches = mutableMapOf<File, () -> Unit>()
		val cache = cache { file, onClosed ->
			closeWatches[file] = onClosed
			AutoCloseable { closeWatches.remove(file) }
		}
		val written = cache.fileForWriting(vaultPath("x.txt"), keepContent = true)
		val evictable = listOf(cachedFile(written.name))
		whenever(dao.loadEvictable()).thenReturn(evictable)

		cache.closedForWriting(written, mock<ParcelFileDescriptor.FileDescriptorDetachedException>(), changed = true) { vaultPath("x.txt") }

		verify(metadataCache).markPendingUpload(vaultPath("x.txt"))
		verify(metadataCache, never()).upload(any(), any(), any(), any())
		verify(metadataCache, never()).abandonUpload(any())
		cache.evictAll()
		assertTrue(written.exists())
		closeWatches.getValue(written)()
		verify(metadataCache).upload(eq(vaultPath("x.txt")), eq(written), any(), any())
		assertTrue(closeWatches.isEmpty())
		cache.evictAll()
		assertFalse(written.exists())
	}

	@Test
	fun `a download whose document is gone before it is recorded is given up`() {
		givenDocument(lastModified = 9_000)
		givenCloudContent("content")
		doThrow(IllegalStateException("FOREIGN KEY constraint failed")).whenever(dao).store(any())
		val cache = cache()

		assertThrows<IllegalStateException> { cache.file(vaultPath("x.txt")) }

		assertEquals(0, directory.listFiles()!!.size)
	}

	private fun captureUploadCallback(copy: File): (VaultPath, VaultPath, Long?) -> Unit {
		whenever(dao.loadLocalNames()).thenReturn(listOf(copy.name))
		cache().upload(vaultPath("x.txt"), copy)
		val callback = argumentCaptor<(VaultPath, VaultPath, Long?) -> Unit>()
		verify(metadataCache).upload(eq(vaultPath("x.txt")), eq(copy), any(), callback.capture())
		return callback.firstValue
	}

	private fun cache(watchClose: (File, () -> Unit) -> AutoCloseable = { _, _ -> AutoCloseable {} }): DocumentContentCache {
		val direct = Executor { it.run() }
		return DocumentContentCache(dao, metadataCache, CloudTaskScheduler(direct, direct, direct), directory, watchClose) { now }
	}

	private fun givenDocument(lastModified: Long, status: String = DocumentMetadataEntity.STATUS_SYNCED, path: String = "x.txt") {
		val metadata = DocumentMetadataEntity().apply {
			vaultId = VAULT_ID
			this.path = path
			parentPath = ""
			name = path
			mimeType = "text/plain"
			this.lastModified = lastModified
			this.status = status
		}
		whenever(metadataCache.requireCurrentDocument(vaultPath(path))).thenReturn(metadata)
		whenever(metadataCache.requireDocument(vaultPath(path))).thenReturn(metadata)
	}

	private fun givenCloudContent(content: String) {
		val handle: CloudFile = mock()
		val stored: CloudFile = mock()
		whenever(repository.file(eq(root), any())).thenReturn(handle)
		whenever(repository.metadata(handle)).thenReturn(stored)
		whenever(repository.read(eq(stored), anyOrNull(), any(), any())).thenAnswer {
			it.getArgument<OutputStream>(2).write(content.toByteArray())
		}
	}

	private fun copy(name: String, content: String, lastModified: Long = now): File {
		return File(directory, name).apply {
			writeText(content)
			setLastModified(lastModified)
		}
	}

	private fun cachedFile(localName: String, path: String = "x.txt", lastModified: Long = 5_000): CachedFileEntity {
		return CachedFileEntity().apply {
			vaultId = VAULT_ID
			this.path = path
			this.localName = localName
			this.lastModified = lastModified
		}
	}

	private fun vaultPath(path: String): VaultPath {
		return VaultPath(vault, path)
	}

	companion object {

		private const val VAULT_ID = 1L
	}
}
