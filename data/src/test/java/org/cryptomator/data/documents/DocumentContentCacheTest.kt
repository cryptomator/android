package org.cryptomator.data.documents

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
import org.cryptomator.domain.VaultPath
import org.cryptomator.domain.exception.NetworkConnectionException
import org.cryptomator.domain.exception.NoSuchCloudFileException
import org.cryptomator.domain.repository.CloudContentRepository
import org.cryptomator.domain.repository.VaultDocumentChangeResult
import org.cryptomator.domain.usecases.ProgressAware
import org.cryptomator.domain.usecases.cloud.DownloadState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InterruptedIOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.util.Date
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import java.util.concurrent.atomic.AtomicInteger

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

	@BeforeEach
	fun setup() {
		whenever(metadataCache.parentFolder(any())).thenReturn(root)
		whenever(metadataCache.withChangesPaused(any<() -> Any?>())).thenAnswer { it.getArgument<() -> Any?>(0)() }
	}

	@Test
	fun `a copy is used while the file has not changed in the cloud`() {
		val copy = copy("copy", "local content")
		whenever(dao.loadLocalNames()).thenReturn(listOf("copy"))
		givenDocument(lastModified = 5_400)
		whenever(dao.load(VAULT_ID, "x.txt")).thenReturn(cachedFile("copy", lastModified = 5_000))

		val file = cache().file(vaultPath("x.txt"))

		assertEquals(copy, file)
		verify(repository, never()).read(any(), any(), any())
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
	fun `copies are kept however old they are until the cache is cleared, which keeps those with a pending upload`() {
		val old = copy("old", "", lastModified = 1_000)
		val recent = copy("recent", "")
		val pending = copy("pending", "", lastModified = 1_000)
		whenever(dao.loadLocalNames()).thenReturn(listOf("old", "recent", "pending"))
		whenever(dao.loadEvictable()).thenReturn(listOf(cachedFile("old", path = "old.txt"), cachedFile("recent", path = "recent.txt")))
		val cache = cache()

		cache.evict()
		assertTrue(old.exists())
		assertTrue(recent.exists())
		verify(dao, never()).delete(any(), any())

		cache.evictAll()
		assertFalse(old.exists())
		assertFalse(recent.exists())
		assertTrue(pending.exists())
		verify(dao).delete(VAULT_ID, "old.txt")
	}

	@Test
	fun `a copy open for writing is not deleted`() {
		givenDocument(lastModified = 5_000, status = DocumentMetadataEntity.STATUS_CREATING)
		val cache = cache()
		val copy = cache.fileForWriting(vaultPath("x.txt"), keepContent = true)
		whenever(dao.loadEvictable()).thenReturn(listOf(cachedFile(copy.name)))

		cache.evictAll()

		assertTrue(copy.exists())
		cache.release(copy)
		cache.evictAll()
		assertFalse(copy.exists())
	}

	@Test
	fun `a download someone waits for is counted until it has ended, and its end is told, unlike an unrecorded download`() {
		givenDocument(lastModified = 9_000)
		val cache = cache()
		var ended = 0
		cache.onAwaitedDownloadsDone = { ended++ }
		var awaitedWhileReading: Boolean? = null
		givenCloudContent("content") { awaitedWhileReading = cache.hasAwaitedDownloads() }

		cache.release(cache.file(vaultPath("x.txt")))

		assertEquals(true, awaitedWhileReading)
		assertFalse(cache.hasAwaitedDownloads())
		assertEquals(1, ended)

		cache.downloadUnrecorded(vaultPath("x.txt"), File(directory, "unrecorded"))

		assertEquals(false, awaitedWhileReading)
		assertEquals(1, ended)
	}

	@Test
	fun `a copy that is being downloaded is not deleted`() {
		givenDocument(lastModified = 9_000)
		val cache = cache()
		givenCloudContent("content") { cache.evictAll() }

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

		onUploaded.onUploaded(vaultPath("x.txt"), vaultPath("x.txt"), 9_000L, true)

		verify(dao).updateLastModified(VAULT_ID, "x.txt", 9_000L)
		verify(dao, never()).delete(any(), any())
	}

	@Test
	fun `an upload as a conflict copy makes the copy the conflict copy's local copy`() {
		val copy = copy("copy", "local changes")
		val onUploaded = captureUploadCallback(copy)

		onUploaded.onUploaded(vaultPath("x.txt"), vaultPath("x (1A2B3).txt"), 9_000L, true)

		verify(dao).delete(VAULT_ID, "x.txt")
		verify(dao).store(argThat { path == "x (1A2B3).txt" && localName == "copy" && lastModified == 9_000L })
	}

	@Test
	fun `an upload as a conflict copy leaves a copy that has been written again since with its file`() {
		val copy = copy("copy", "local changes")
		val onUploaded = captureUploadCallback(copy)

		onUploaded.onUploaded(vaultPath("x.txt"), vaultPath("x (1A2B3).txt"), 9_000L, false)

		verify(dao, never()).delete(any(), any())
		verify(dao, never()).store(any())
	}

	@Test
	fun `an upload as a conflict copy leaves a copy that has been written again after the upload was recorded with its file`() {
		val copy = copy("copy", "local changes")
		val onUploaded = captureUploadCallback(copy)
		whenever(metadataCache.isUploading(vaultPath("x.txt"))).thenReturn(true)

		onUploaded.onUploaded(vaultPath("x.txt"), vaultPath("x (1A2B3).txt"), 9_000L, true)

		verify(dao, never()).delete(any(), any())
		verify(dao, never()).store(any())
	}

	@Test
	fun `an upload as a conflict copy leaves the copy with its file if the conflict copy has been written meanwhile`() {
		val copy = copy("copy", "local changes")
		val onUploaded = captureUploadCallback(copy)
		whenever(dao.load(VAULT_ID, "x (1A2B3).txt")).thenReturn(cachedFile("newer", path = "x (1A2B3).txt"))

		onUploaded.onUploaded(vaultPath("x.txt"), vaultPath("x (1A2B3).txt"), 9_000L, true)

		verify(dao, never()).delete(any(), any())
		verify(dao, never()).store(any())
	}

	@Test
	fun `an upload as a conflict copy leaves a copy that is still open for writing with its file`() {
		val stored = givenStoredCopies(cachedFile("copy"))
		copy("copy", "local changes")
		givenDocument(lastModified = 5_000)
		val cache = cache()
		val written = cache.fileForWriting(vaultPath("x.txt"), keepContent = false)
		cache.upload(vaultPath("x.txt"), written)
		val onUploaded = argumentCaptor<DocumentMetadataCache.OnUploaded>()
		verify(metadataCache).upload(eq(vaultPath("x.txt")), eq(written), any(), any(), anyOrNull(), any(), onUploaded.capture())

		onUploaded.firstValue.onUploaded(vaultPath("x.txt"), vaultPath("x (1A2B3).txt"), 9_000L, true)

		assertEquals("copy", written.name)
		assertEquals("copy", stored.getValue("x.txt").localName)
		assertNull(stored["x (1A2B3).txt"])
		verify(dao, never()).delete(any(), any())
		verify(metadataCache).markPendingUpload(vaultPath("x.txt"))
	}

	@Test
	fun `an earlier upload ending after the copy has been replaced leaves the replacement in place`() {
		val copy = copy("copy", "local changes")
		val onUploaded = captureUploadCallback(copy)
		whenever(dao.load(VAULT_ID, "x.txt")).thenReturn(cachedFile("replacement"))

		onUploaded.onUploaded(vaultPath("x.txt"), vaultPath("x (1A2B3).txt"), 9_000L, true)
		onUploaded.onUploaded(vaultPath("x.txt"), vaultPath("x.txt"), 9_000L, true)

		verify(dao, never()).delete(any(), any())
		verify(dao, never()).store(any())
		verify(dao, never()).updateLastModified(any(), any(), anyOrNull())
	}

	@Test
	fun `a writer whose copy has been replaced while it was open records its copy again before uploading it`() {
		val stored = givenStoredCopies()
		givenDocument(lastModified = 5_000, status = DocumentMetadataEntity.STATUS_CREATING)
		val cache = cache()
		val written = cache.fileForWriting(vaultPath("x.txt"), keepContent = false)
		stored["x.txt"] = cachedFile("replacement", lastModified = 7_000)

		cache.closedForWriting(written, null, changed = true) { vaultPath("x.txt") }

		assertEquals(written.name, stored.getValue("x.txt").localName)
		assertEquals(5_000L, stored.getValue("x.txt").lastModified)
		verify(metadataCache).upload(eq(vaultPath("x.txt")), eq(written), any(), any(), anyOrNull(), any(), any())
		whenever(dao.loadLocalNames()).thenReturn(listOf(written.name))
		whenever(metadataCache.pendingUploads(vault)).thenReturn(listOf(vaultPath("x.txt")))
		whenever(metadataCache.isUploading(vaultPath("x.txt"))).thenReturn(true)
		givenDocument(lastModified = 7_000, status = DocumentMetadataEntity.STATUS_UPLOADING)
		val restarted = cache()
		assertEquals(written, restarted.file(vaultPath("x.txt")))
		restarted.resumeUploads(vault)
		verify(metadataCache, times(2)).upload(eq(vaultPath("x.txt")), eq(written), any(), any(), anyOrNull(), any(), any())
	}

	@Test
	fun `a writer whose copy a pending import replaced keeps its copy open and saves it as a conflict copy`() {
		val stored = givenStoredCopies()
		givenDocument(lastModified = 5_000, status = DocumentMetadataEntity.STATUS_CREATING)
		givenImportTarget("x.txt", isNew = false, lastModified = 5_000)
		whenever(metadataCache.upload(any(), any(), any(), any(), anyOrNull(), any(), any())).thenReturn(mock())
		whenever(metadataCache.createConflictCopy(vaultPath("x.txt"))).thenReturn(vaultPath("x (1A2B3).txt"))
		val cache = cache()
		val written = cache.fileForWriting(vaultPath("x.txt"), keepContent = false)
		written.writeText("writer")

		cache.importFile(VaultPath(vault), "x.txt", replace = true, requireListing = false, progressAware = mock(), modifiedDate = null) { it.write("import".toByteArray()) }
		val imported = stored.getValue("x.txt").localName
		whenever(metadataCache.isUploading(vaultPath("x.txt"))).thenReturn(true)
		assertTrue(written.exists())
		cache.closedForWriting(written, null, changed = true) { vaultPath("x.txt") }

		assertEquals(imported, stored.getValue("x.txt").localName)
		assertEquals(written.name, stored.getValue("x (1A2B3).txt").localName)
		assertNull(stored.getValue("x (1A2B3).txt").lastModified)
		verify(metadataCache).upload(eq(vaultPath("x (1A2B3).txt")), eq(written), any(), any(), anyOrNull(), any(), any())
		cache.evictAll()
		assertEquals("writer", written.readText())
		assertEquals("import", File(directory, imported).readText())
	}

	@Test
	fun `a download doesn't replace a write that was recorded while it ran`() {
		givenDocument(lastModified = 9_000)
		val stored = givenStoredCopies()
		givenCloudContent("cloud") { stored["x.txt"] = cachedFile("written", lastModified = 5_000) }
		val cache = cache()

		val downloaded = cache.file(vaultPath("x.txt"))

		assertEquals("cloud", downloaded.readText())
		assertEquals("written", stored.getValue("x.txt").localName)
		cache.release(downloaded)
		cache.evictAll()
		assertFalse(downloaded.exists())
	}

	@Test
	fun `a download doesn't replace a copy that was written to while it ran`() {
		val copy = copy("copy", "old content")
		whenever(dao.loadLocalNames()).thenReturn(listOf("copy"))
		givenDocument(lastModified = 9_000)
		whenever(dao.load(VAULT_ID, "x.txt")).thenReturn(cachedFile("copy", lastModified = 5_000))
		val writtenMeanwhile = AtomicBoolean()
		whenever(metadataCache.isUploading(vaultPath("x.txt"))).thenAnswer { writtenMeanwhile.get() }
		givenCloudContent("cloud") {
			copy.writeText("local changes")
			writtenMeanwhile.set(true)
		}

		val downloaded = cache().file(vaultPath("x.txt"))

		assertEquals("cloud", downloaded.readText())
		verify(dao, never()).store(any())
		assertEquals("local changes", copy.readText())
	}

	@Test
	fun `a writer whose download a later write overtook keeps the version it downloaded as its base`() {
		givenDocument(lastModified = 5_000)
		val stored = givenStoredCopies()
		givenCloudContent("cloud") { stored["x.txt"] = cachedFile("imported", lastModified = 9_000) }
		val cache = cache()
		val written = cache.fileForWriting(vaultPath("x.txt"), keepContent = true)

		cache.closedForWriting(written, null, changed = true) { vaultPath("x.txt") }

		assertEquals(written.name, stored.getValue("x.txt").localName)
		assertEquals(5_000L, stored.getValue("x.txt").lastModified)
	}

	@Test
	fun `a pending upload that can't be resumed doesn't keep the others from being resumed`() {
		val copyX = copy("copy-x", "x")
		val copyY = copy("copy-y", "y")
		whenever(dao.loadLocalNames()).thenReturn(listOf("copy-x", "copy-y"))
		whenever(dao.load(VAULT_ID, "x.txt")).thenReturn(cachedFile("copy-x"))
		whenever(dao.load(VAULT_ID, "y.txt")).thenReturn(cachedFile("copy-y", path = "y.txt"))
		whenever(metadataCache.pendingUploads(vault)).thenReturn(listOf(vaultPath("x.txt"), vaultPath("y.txt")))
		whenever(metadataCache.isUploading(any())).thenReturn(true)
		whenever(metadataCache.upload(eq(vaultPath("x.txt")), any(), any(), any(), anyOrNull(), any(), any())).thenAnswer { throw FileNotFoundException("gone") }
		val cache = cache()

		cache.resumeUploads(vault)

		verify(metadataCache).upload(eq(vaultPath("x.txt")), eq(copyX), any(), any(), anyOrNull(), any(), any())
		verify(metadataCache).upload(eq(vaultPath("y.txt")), eq(copyY), any(), any(), anyOrNull(), any(), any())
	}

	@Test
	fun `pending uploads are resumed whenever the app asks for it, unless they are running`() {
		val copy = copy("copy", "local changes")
		whenever(dao.loadLocalNames()).thenReturn(listOf("copy"))
		whenever(dao.load(VAULT_ID, "x.txt")).thenReturn(cachedFile("copy"))
		val cache = cache()

		cache.resumeUploads(vault)
		whenever(metadataCache.pendingUploads(vault)).thenReturn(listOf(vaultPath("x.txt")))
		whenever(metadataCache.isUploading(vaultPath("x.txt"))).thenReturn(true)
		cache.resumeUploads(vault)
		whenever(metadataCache.hasUploadInFlight(vaultPath("x.txt"))).thenReturn(true)
		cache.resumeUploads(vault)

		verify(metadataCache).upload(eq(vaultPath("x.txt")), eq(copy), any(), any(), anyOrNull(), any(), any())
	}

	@Test
	fun `the provider resumes pending uploads once per vault`() {
		val copy = copy("copy", "local changes")
		whenever(dao.loadLocalNames()).thenReturn(listOf("copy"))
		whenever(dao.load(VAULT_ID, "x.txt")).thenReturn(cachedFile("copy"))
		val pendingUploads = listOf(vaultPath("x.txt"))
		whenever(metadataCache.pendingUploads(vault)).thenReturn(pendingUploads)
		whenever(metadataCache.isUploading(vaultPath("x.txt"))).thenReturn(true)
		val cache = cache()

		cache.resumeUploadsOnce(vault)
		cache.resumeUploadsOnce(vault)

		verify(metadataCache).upload(eq(vaultPath("x.txt")), eq(copy), any(), any(), anyOrNull(), any(), any())
	}

	@Test
	fun `an imported file is recorded before it is uploaded, so that it is kept and resumed after a restart`() {
		givenImportTarget("x.txt", isNew = true)
		whenever(metadataCache.upload(any(), any(), any(), any(), anyOrNull(), any(), any())).thenReturn(mock())
		val cache = cache()

		cache.importFile(VaultPath(vault), "x.txt", replace = false, requireListing = true, progressAware = mock(), modifiedDate = 4_000) { it.write("imported".toByteArray()) }

		val stored = argumentCaptor<CachedFileEntity>()
		inOrder(metadataCache, dao) {
			verify(metadataCache).prepareFolder(VaultPath(vault), true)
			verify(metadataCache).markPendingUpload(vaultPath("x.txt"))
			verify(dao).store(stored.capture())
			verify(metadataCache).upload(eq(vaultPath("x.txt")), argThat { name == stored.firstValue.localName }, any(), any(), eq(4_000L), any(), any())
		}
		val imported = File(directory, stored.firstValue.localName)
		assertEquals("imported", imported.readText())
		assertNotEquals(4_000L, imported.lastModified())
		assertNull(stored.firstValue.lastModified)
		whenever(dao.loadLocalNames()).thenReturn(listOf(imported.name))
		whenever(dao.load(VAULT_ID, "x.txt")).thenReturn(stored.firstValue)
		whenever(metadataCache.pendingUploads(vault)).thenReturn(listOf(vaultPath("x.txt")))
		whenever(metadataCache.isUploading(vaultPath("x.txt"))).thenReturn(true)
		val restarted = cache()
		assertTrue(imported.exists())
		restarted.resumeUploads(vault)
		verify(metadataCache, times(2)).upload(eq(vaultPath("x.txt")), eq(imported), any(), any(), anyOrNull(), any(), any())
	}

	@Test
	fun `an import that could not be copied completely leaves no pending file`() {
		givenImportTarget("x.txt", isNew = true)
		val cache = cache()

		assertThrows<InterruptedIOException> {
			cache.importFile(VaultPath(vault), "x.txt", replace = false, requireListing = false, progressAware = mock(), modifiedDate = null) {
				it.write("partly".toByteArray())
				throw InterruptedIOException()
			}
		}

		verify(metadataCache).abandonUpload(vaultPath("x.txt"))
		verify(dao, never()).store(any())
		verify(metadataCache, never()).upload(any(), any(), any(), any(), anyOrNull(), any(), any())
		assertEquals(0, directory.listFiles()!!.size)
	}

	@Test
	fun `a new import that could not be copied completely keeps a copy someone else wrote meanwhile`() {
		givenImportTarget("x.txt", isNew = true)
		val cache = cache()

		assertThrows<InterruptedIOException> {
			cache.importFile(VaultPath(vault), "x.txt", replace = false, requireListing = false, progressAware = mock(), modifiedDate = null) {
				whenever(dao.load(VAULT_ID, "x.txt")).thenReturn(cachedFile("written"))
				throw InterruptedIOException()
			}
		}

		verify(metadataCache, never()).abandonUpload(any())
	}

	@Test
	fun `an import whose file is gone before it is recorded is given up`() {
		whenever(metadataCache.createFile(vaultPath("x.txt"), false)).thenReturn(DocumentMetadataCache.FileTarget(7L, isNew = true))
		val cache = cache()

		assertThrows<NoSuchCloudFileException> {
			cache.importFile(VaultPath(vault), "x.txt", replace = false, requireListing = false, progressAware = mock(), modifiedDate = null) { it.write("imported".toByteArray()) }
		}

		verify(dao, never()).store(any())
		assertEquals(0, directory.listFiles()!!.size)
	}

	@Test
	fun `a replacing import that could not be copied completely keeps the earlier copy`() {
		val pending = copy("pending", "earlier write")
		whenever(dao.loadLocalNames()).thenReturn(listOf("pending"))
		whenever(dao.load(VAULT_ID, "x.txt")).thenReturn(cachedFile("pending"))
		givenImportTarget("x.txt", isNew = false)
		val cache = cache()

		assertThrows<InterruptedIOException> {
			cache.importFile(VaultPath(vault), "x.txt", replace = true, requireListing = false, progressAware = mock(), modifiedDate = null) {
				it.write("partly".toByteArray())
				throw InterruptedIOException()
			}
		}

		assertEquals("earlier write", pending.readText())
		assertEquals(listOf(pending), directory.listFiles()!!.toList())
		verify(dao, never()).store(any())
		verify(metadataCache, never()).abandonUpload(any())
		verify(metadataCache, never()).markPendingUpload(any())
	}

	@Test
	fun `a replacing import is marked pending before its copy replaces the earlier one, based on the file's version in the cache`() {
		val earlier = copy("earlier", "earlier write")
		whenever(dao.loadLocalNames()).thenReturn(listOf("earlier"))
		whenever(dao.load(VAULT_ID, "x.txt")).thenReturn(cachedFile("earlier", lastModified = 3_000))
		givenImportTarget("x.txt", isNew = false, lastModified = 5_000)
		whenever(metadataCache.upload(any(), any(), any(), any(), anyOrNull(), any(), any())).thenReturn(mock())
		val cache = cache()

		cache.importFile(VaultPath(vault), "x.txt", replace = true, requireListing = false, progressAware = mock(), modifiedDate = null) { it.write("replacement".toByteArray()) }

		inOrder(metadataCache, dao) {
			verify(metadataCache).markPendingUpload(vaultPath("x.txt"))
			verify(dao).store(argThat { localName != "earlier" && lastModified == 5_000L })
			verify(metadataCache).upload(eq(vaultPath("x.txt")), any(), any(), any(), anyOrNull(), any(), any())
		}
		assertFalse(earlier.exists())
	}

	@Test
	fun `an import follows its file if it is renamed while the content is copied`() {
		givenImportTarget("x.txt", isNew = false, lastModified = 5_000, currentPath = "renamed.txt")
		whenever(metadataCache.upload(any(), any(), any(), any(), anyOrNull(), any(), any())).thenReturn(mock())
		val cache = cache()

		cache.importFile(VaultPath(vault), "x.txt", replace = true, requireListing = false, progressAware = mock(), modifiedDate = null) { it.write("replacement".toByteArray()) }

		verify(dao).store(argThat { path == "renamed.txt" })
		verify(metadataCache).upload(eq(vaultPath("renamed.txt")), any(), any(), any(), anyOrNull(), any(), any())
	}

	@Test
	fun `canceling an import drops it unless it has been written again since`() {
		val imported = copy("imported", "imported")
		whenever(dao.loadLocalNames()).thenReturn(listOf("imported"))
		whenever(dao.load(VAULT_ID, "x.txt")).thenReturn(cachedFile("imported"))
		val cache = cache()
		val superseded = upload(localFile = imported, current = null)
		val replaced = upload(localFile = File(directory, "replaced"), current = DocumentMetadataEntity().apply { path = "x.txt" })
		val own = upload(localFile = imported, current = DocumentMetadataEntity().apply { path = "x.txt" })

		listOf(superseded, replaced).forEach {
			cache.cancel(it)
			it.result.completeExceptionally(CancellationException())
		}
		verify(dao, never()).delete(any(), any())
		verify(metadataCache, never()).abandonUpload(any())
		assertTrue(imported.exists())

		cache.cancel(own)
		verify(own).stop()
		own.result.completeExceptionally(CancellationException())
		verify(dao).delete(VAULT_ID, "x.txt")
		verify(metadataCache).abandonUpload(vaultPath("x.txt"))
		assertFalse(imported.exists())
	}

	@Test
	fun `canceling an import whose upload is done does nothing`() {
		val cache = cache()
		val done = upload(localFile = copy("imported", "imported"), current = DocumentMetadataEntity().apply { path = "x.txt" })
		done.result.complete(mock())

		cache.cancel(done)

		verify(done, never()).stop()
		verify(dao, never()).delete(any(), any())
	}

	@Test
	fun `a copy written with an error is dropped together with its pending upload`() {
		val copy = copy("copy", "partly written")
		whenever(dao.loadLocalNames()).thenReturn(listOf("copy"))
		whenever(dao.load(VAULT_ID, "x.txt")).thenReturn(cachedFile("copy"))

		cache().discard(vaultPath("x.txt"), copy)

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
	fun `copies are kept however large the cache gets`() {
		val older = copy("older", "")
		val newer = copy("newer", "")
		RandomAccessFile(older, "rw").use { it.setLength(300L * 1024 * 1024) }
		RandomAccessFile(newer, "rw").use { it.setLength(300L * 1024 * 1024) }
		whenever(dao.loadLocalNames()).thenReturn(listOf("older", "newer"))
		whenever(dao.loadEvictable()).thenReturn(listOf(cachedFile("older", path = "older.txt"), cachedFile("newer", path = "newer.txt")))

		cache().evict()

		assertTrue(older.exists())
		assertTrue(newer.exists())
		verify(dao, never()).delete(any(), any())
	}

	@Test
	@Timeout(10)
	fun `a canceled download keeps its copy and its place in the order of requests until it has stopped`() {
		givenDocument(lastModified = 9_000)
		val downloads = LinkedBlockingQueue<Runnable>()
		val scheduler = CloudTaskScheduler({ it.run() }, { it.run() }, { downloads.put(it) })
		val cache = DocumentContentCache(dao, metadataCache, scheduler, repository, directory, null) { _, _ -> AutoCloseable {} }
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
		givenCloudContent("") {
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
	fun `a writer whose copy has been replaced leaves the replacement alone when its write fails`() {
		givenDocument(lastModified = 5_000)
		val replacement = copy("replacement", "replacement")
		whenever(dao.loadLocalNames()).thenReturn(listOf("replacement"))
		val cache = cache()
		val written = cache.fileForWriting(vaultPath("x.txt"), keepContent = false)
		whenever(dao.load(VAULT_ID, "x.txt")).thenReturn(cachedFile("replacement"))

		cache.closedForWriting(written, IOException("writer died"), changed = true) { vaultPath("x.txt") }

		verify(dao, never()).delete(any(), any())
		verify(metadataCache, never()).abandonUpload(any())
		assertTrue(replacement.exists())
	}

	@Test
	fun `uploads whose local copy was never recorded are given up when the cache is created`() {
		cache()

		verify(metadataCache).abandonUploadsWithoutLocalCopy()
	}

	@Test
	fun `the uploads a rejected deletion stopped are resumed`() {
		val result = CompletableFuture<Unit>()
		whenever(metadataCache.delete(vaultPath("a"), false)).thenReturn(DocumentMetadataCache.Deletion(emptyList(), result))
		val cache = cache()

		cache.delete(vaultPath("a"))
		verify(metadataCache, never()).pendingUploads(any())
		result.completeExceptionally(IOException("offline"))

		verify(metadataCache).pendingUploads(vault)
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
		verify(metadataCache, never()).upload(any(), any(), any(), any(), anyOrNull(), any(), any())
	}

	@Test
	fun `a changed copy is uploaded when it is closed, an unchanged one is not`() {
		givenDocument(lastModified = 5_000, status = DocumentMetadataEntity.STATUS_CREATING)
		val cache = cache()
		val unchanged = cache.fileForWriting(vaultPath("x.txt"), keepContent = true)
		cache.closedForWriting(unchanged, null, changed = false) { vaultPath("x.txt") }
		verify(metadataCache, never()).upload(any(), any(), any(), any(), anyOrNull(), any(), any())

		val changed = cache.fileForWriting(vaultPath("x.txt"), keepContent = true)
		cache.closedForWriting(changed, null, changed = true) { vaultPath("x.txt") }

		verify(metadataCache).upload(eq(vaultPath("x.txt")), eq(changed), any(), any(), anyOrNull(), any(), any())
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
		whenever(dao.load(VAULT_ID, "x.txt")).thenReturn(cachedFile(written.name))

		cache.closedForWriting(written, mock<ParcelFileDescriptor.FileDescriptorDetachedException>(), changed = true) { vaultPath("x.txt") }

		verify(metadataCache).markPendingUpload(vaultPath("x.txt"))
		verify(metadataCache, never()).upload(any(), any(), any(), any(), anyOrNull(), any(), any())
		verify(metadataCache, never()).abandonUpload(any())
		cache.evictAll()
		assertTrue(written.exists())
		closeWatches.getValue(written)()
		verify(metadataCache).upload(eq(vaultPath("x.txt")), eq(written), any(), any(), anyOrNull(), any(), any())
		assertTrue(closeWatches.isEmpty())
		cache.evictAll()
		assertFalse(written.exists())
	}

	@Test
	fun `a detached writer whose copy an uploaded write replaced records its copy again as a pending write right away`() {
		val stored = givenStoredCopies()
		givenDocument(lastModified = 5_000, status = DocumentMetadataEntity.STATUS_CREATING)
		val cache = cache()
		val written = cache.fileForWriting(vaultPath("x.txt"), keepContent = true)
		stored["x.txt"] = cachedFile("replacement", lastModified = 7_000)

		cache.closedForWriting(written, mock<ParcelFileDescriptor.FileDescriptorDetachedException>(), changed = true) { vaultPath("x.txt") }

		assertEquals(written.name, stored.getValue("x.txt").localName)
		assertEquals(5_000L, stored.getValue("x.txt").lastModified)
		verify(metadataCache).markPendingUpload(vaultPath("x.txt"))
		verify(metadataCache, never()).upload(any(), any(), any(), any(), anyOrNull(), any(), any())
	}

	@Test
	fun `a writer whose copy an uploaded write replaced marks its file pending before recording the copy again`() {
		val stored = givenStoredCopies()
		givenDocument(lastModified = 5_000, status = DocumentMetadataEntity.STATUS_CREATING)
		val cache = cache()
		val written = cache.fileForWriting(vaultPath("x.txt"), keepContent = true)
		stored["x.txt"] = cachedFile("replacement", lastModified = 7_000)

		cache.closedForWriting(written, null, changed = true) { vaultPath("x.txt") }

		inOrder(metadataCache, dao) {
			verify(metadataCache).markPendingUpload(vaultPath("x.txt"))
			verify(dao).store(argThat { localName == written.name })
			verify(metadataCache).upload(eq(vaultPath("x.txt")), eq(written), any(), any(), anyOrNull(), any(), any())
		}
	}

	@Test
	fun `a detached writer whose copy a pending import replaced is kept as a pending conflict copy right away`() {
		val stored = givenStoredCopies()
		givenDocument(lastModified = 5_000, status = DocumentMetadataEntity.STATUS_CREATING)
		whenever(metadataCache.createConflictCopy(vaultPath("x.txt"))).thenReturn(vaultPath("x (1A2B3).txt"))
		val closeWatches = mutableMapOf<File, () -> Unit>()
		val cache = cache { file, onClosed ->
			closeWatches[file] = onClosed
			AutoCloseable { closeWatches.remove(file) }
		}
		val written = cache.fileForWriting(vaultPath("x.txt"), keepContent = true)
		stored["x.txt"] = cachedFile("imported")
		whenever(metadataCache.isUploading(vaultPath("x.txt"))).thenReturn(true)

		cache.closedForWriting(written, mock<ParcelFileDescriptor.FileDescriptorDetachedException>(), changed = true) { vaultPath("x.txt") }

		assertEquals("imported", stored.getValue("x.txt").localName)
		assertEquals(written.name, stored.getValue("x (1A2B3).txt").localName)
		verify(metadataCache).createConflictCopy(vaultPath("x.txt"))
		closeWatches.getValue(written)()
		verify(metadataCache).createConflictCopy(vaultPath("x.txt"))
		verify(metadataCache).upload(eq(vaultPath("x (1A2B3).txt")), eq(written), any(), any(), anyOrNull(), any(), any())
	}

	@Test
	fun `a detached writer whose file is gone gives up its copy`() {
		givenDocument(lastModified = 5_000, status = DocumentMetadataEntity.STATUS_CREATING)
		val cache = cache()
		val written = cache.fileForWriting(vaultPath("x.txt"), keepContent = true)

		assertThrows<FileNotFoundException> {
			cache.closedForWriting(written, mock<ParcelFileDescriptor.FileDescriptorDetachedException>(), changed = true) { throw FileNotFoundException("gone") }
		}

		cache.evictAll()
		assertFalse(written.exists())
	}

	@Test
	fun `two writers of a copy a pending import replaced keep their saves in one conflict copy`() {
		val stored = givenStoredCopies(cachedFile("copy", lastModified = 5_000))
		copy("copy", "content")
		givenDocument(lastModified = 5_000)
		givenImportTarget("x.txt", isNew = false, lastModified = 5_000)
		whenever(metadataCache.upload(any(), any(), any(), any(), anyOrNull(), any(), any())).thenReturn(mock())
		whenever(metadataCache.createConflictCopy(vaultPath("x.txt"))).thenReturn(vaultPath("x (1A2B3).txt"), vaultPath("x (4C5D6).txt"))
		val cache = cache()
		val first = cache.fileForWriting(vaultPath("x.txt"), keepContent = true)
		val second = cache.fileForWriting(vaultPath("x.txt"), keepContent = true)
		cache.importFile(VaultPath(vault), "x.txt", replace = true, requireListing = false, progressAware = mock(), modifiedDate = null) { it.write("import".toByteArray()) }
		whenever(metadataCache.isUploading(vaultPath("x.txt"))).thenReturn(true)

		cache.closedForWriting(first, null, changed = true) { vaultPath("x.txt") }
		cache.closedForWriting(second, null, changed = true) { vaultPath("x.txt") }

		verify(metadataCache).createConflictCopy(vaultPath("x.txt"))
		assertEquals("copy", stored.getValue("x (1A2B3).txt").localName)
		assertNull(stored["x (4C5D6).txt"])
		verify(metadataCache, times(2)).upload(eq(vaultPath("x (1A2B3).txt")), eq(first), any(), any(), anyOrNull(), any(), any())
	}

	@Test
	fun `a writer whose copy became a conflict copy while it was open saves it there`() {
		val stored = givenStoredCopies(cachedFile("copy", lastModified = 5_000))
		copy("copy", "content")
		givenDocument(lastModified = 5_000)
		val cache = cache()
		val written = cache.fileForWriting(vaultPath("x.txt"), keepContent = true)
		stored.remove("x.txt")
		stored["x (1A2B3).txt"] = cachedFile("copy", path = "x (1A2B3).txt", lastModified = 9_000)

		cache.closedForWriting(written, null, changed = true) { vaultPath("x.txt") }

		assertNull(stored["x.txt"])
		verify(metadataCache).upload(eq(vaultPath("x (1A2B3).txt")), eq(written), any(), any(), anyOrNull(), any(), any())
	}

	@Test
	fun `a writer of a copy with a pending upload keeps the version of that copy as its base`() {
		val stored = givenStoredCopies(cachedFile("copy", lastModified = 5_000))
		copy("copy", "local changes")
		givenDocument(lastModified = 9_000, status = DocumentMetadataEntity.STATUS_UPLOADING)
		val cache = cache()
		val written = cache.fileForWriting(vaultPath("x.txt"), keepContent = true)
		stored["x.txt"] = cachedFile("later", lastModified = 9_000)

		cache.closedForWriting(written, null, changed = true) { vaultPath("x.txt") }

		assertEquals("copy", stored.getValue("x.txt").localName)
		assertEquals(5_000L, stored.getValue("x.txt").lastModified)
	}

	@Test
	fun `a copy stays with its file while a writer has it open, also once a reader released it`() {
		val stored = givenStoredCopies(cachedFile("copy", lastModified = 5_000))
		copy("copy", "content")
		givenDocument(lastModified = 5_000)
		val cache = cache()
		val read = cache.file(vaultPath("x.txt"))
		val written = cache.fileForWriting(vaultPath("x.txt"), keepContent = true)
		cache.release(read)
		cache.upload(vaultPath("x.txt"), written)
		val onUploaded = argumentCaptor<DocumentMetadataCache.OnUploaded>()
		verify(metadataCache).upload(eq(vaultPath("x.txt")), eq(written), any(), any(), anyOrNull(), any(), onUploaded.capture())

		onUploaded.firstValue.onUploaded(vaultPath("x.txt"), vaultPath("x (1A2B3).txt"), 9_000L, true)
		assertEquals("copy", stored.getValue("x.txt").localName)
		assertNull(stored["x (1A2B3).txt"])

		cache.closedForWriting(written, null, changed = false) { vaultPath("x.txt") }
		onUploaded.firstValue.onUploaded(vaultPath("x.txt"), vaultPath("x (1A2B3).txt"), 9_000L, true)
		assertEquals("copy", stored.getValue("x (1A2B3).txt").localName)
	}

	@Test
	fun `a download replacing an old copy that the cleared cache deleted is recorded`() {
		val stored = givenStoredCopies(cachedFile("old", lastModified = 5_000))
		val old = copy("old", "old content")
		whenever(dao.loadEvictable()).thenAnswer { stored.values.toList() }
		doAnswer { stored.remove(it.getArgument<String>(1)) }.whenever(dao).delete(any(), any())
		givenDocument(lastModified = 9_000)
		givenCloudContent("new content")
		val cache = cache()
		cache.evictAll()
		assertFalse(old.exists())

		val file = cache.file(vaultPath("x.txt"))

		assertEquals("new content", file.readText())
		assertEquals(file.name, stored.getValue("x.txt").localName)
		assertEquals(9_000L, stored.getValue("x.txt").lastModified)
	}

	@Test
	fun `a copy written from scratch is given up if it can't be recorded`() {
		givenDocument(lastModified = 7_000)
		doThrow(IllegalStateException("FOREIGN KEY constraint failed")).whenever(dao).store(any())
		val cache = cache()

		assertThrows<IllegalStateException> { cache.fileForWriting(vaultPath("x.txt"), keepContent = false) }

		assertEquals(0, directory.listFiles()!!.size)
	}

	@Test
	fun `an import whose upload can't be started is kept as a pending upload`() {
		val stored = givenStoredCopies(cachedFile("earlier"))
		copy("earlier", "earlier write")
		givenImportTarget("x.txt", isNew = false, lastModified = 5_000)
		whenever(metadataCache.upload(any(), any(), any(), any(), anyOrNull(), any(), any())).thenAnswer { throw IllegalStateException("disk full") }
		val cache = cache()

		assertThrows<IllegalStateException> {
			cache.importFile(VaultPath(vault), "x.txt", replace = true, requireListing = false, progressAware = mock(), modifiedDate = null) { it.write("replacement".toByteArray()) }
		}

		val imported = File(directory, stored.getValue("x.txt").localName)
		assertEquals("replacement", imported.readText())
		verify(metadataCache).markPendingUpload(vaultPath("x.txt"))
		verify(metadataCache, never()).abandonUpload(any())
		// the import's claim is given up, only its record keeps the copy
		assertTrue(imported.setLastModified(now))
		whenever(dao.loadEvictable()).thenReturn(stored.values.toList())
		cache.evictAll()
		assertFalse(imported.exists())
	}

	@Test
	fun `an import that failed reports its own error, also if cleaning up after it fails`() {
		givenImportTarget("x.txt", isNew = true)
		doThrow(IllegalStateException("disk full")).whenever(metadataCache).abandonUpload(any())
		val cache = cache()

		assertThrows<InterruptedIOException> {
			cache.importFile(VaultPath(vault), "x.txt", replace = false, requireListing = false, progressAware = mock(), modifiedDate = null) { throw InterruptedIOException() }
		}
	}

	@Test
	fun `an uploaded import's copy can be evicted`() {
		val stored = givenStoredCopies()
		givenImportTarget("x.txt", isNew = true)
		whenever(metadataCache.upload(any(), any(), any(), any(), anyOrNull(), any(), any())).thenReturn(mock())
		val cache = cache()
		cache.importFile(VaultPath(vault), "x.txt", replace = false, requireListing = false, progressAware = mock(), modifiedDate = null) { it.write("imported".toByteArray()) }
		val imported = File(directory, stored.getValue("x.txt").localName)
		assertTrue(imported.setLastModified(now))
		whenever(dao.loadEvictable()).thenReturn(stored.values.toList())

		cache.evictAll()

		assertFalse(imported.exists())
	}

	@Test
	fun `canceling an import keeps its file while it is open for writing`() {
		val stored = givenStoredCopies(cachedFile("imported"))
		val imported = copy("imported", "imported")
		givenDocument(lastModified = 5_000, status = DocumentMetadataEntity.STATUS_UPLOADING)
		val cache = cache()
		val written = cache.fileForWriting(vaultPath("x.txt"), keepContent = true)
		assertEquals(imported, written)
		val import = upload(localFile = imported, current = DocumentMetadataEntity().apply { path = "x.txt" })

		cache.cancel(import)
		import.result.completeExceptionally(CancellationException())

		verify(import).stop()
		verify(dao, never()).delete(any(), any())
		verify(metadataCache, never()).abandonUpload(any())
		assertEquals("imported", stored.getValue("x.txt").localName)
		assertTrue(imported.exists())
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

	@Test
	fun `a copy to write from scratch that can't be recorded keeps the copy of a pending write`() {
		val stored = givenStoredCopies(cachedFile("pending", lastModified = 5_000))
		val pending = copy("pending", "earlier write")
		givenDocument(lastModified = 7_000, status = DocumentMetadataEntity.STATUS_UPLOADING)
		val cache = cache()
		doThrow(IllegalStateException("disk full")).whenever(dao).store(any())

		assertThrows<IllegalStateException> { cache.fileForWriting(vaultPath("x.txt"), keepContent = false) }

		assertEquals("earlier write", pending.readText())
		assertEquals("pending", stored.getValue("x.txt").localName)
		whenever(dao.loadEvictable()).thenReturn(stored.values.toList())
		cache.evictAll()
		assertFalse(pending.exists())
	}

	@Test
	fun `a closed writer's copy is recorded and uploaded where its file is while no change is made to the cache`() {
		givenStoredCopies()
		givenDocument(lastModified = 5_000, status = DocumentMetadataEntity.STATUS_CREATING)
		val cache = cache()
		val written = cache.fileForWriting(vaultPath("x.txt"), keepContent = false)
		val depth = AtomicInteger()
		val paused = recordWhetherChangesPaused(depth)
		val pausedWhileLookedUp = mutableListOf<Boolean>()

		cache.closedForWriting(written, null, changed = true) {
			pausedWhileLookedUp.add(depth.get() > 0)
			vaultPath("x.txt")
		}

		assertEquals(listOf(true), pausedWhileLookedUp)
		assertEquals(listOf(true, true), paused)
	}

	@Test
	fun `a canceled upload is dropped while no change is made to the cache`() {
		givenStoredCopies(cachedFile("imported"))
		val imported = copy("imported", "imported")
		val cache = cache()
		val paused = recordWhetherChangesPaused()
		val import = upload(localFile = imported, current = DocumentMetadataEntity().apply { path = "x.txt" })

		cache.cancel(import)
		import.result.completeExceptionally(CancellationException())

		assertEquals(listOf(true, true), paused)
	}

	@Test
	fun `a new import that could not be copied completely drops its file also if it has been renamed meanwhile`() {
		givenImportTarget("x.txt", isNew = true, currentPath = "renamed.txt")
		val cache = cache()

		assertThrows<InterruptedIOException> {
			cache.importFile(VaultPath(vault), "x.txt", replace = false, requireListing = false, progressAware = mock(), modifiedDate = null) { throw InterruptedIOException() }
		}

		verify(metadataCache).abandonUpload(vaultPath("renamed.txt"))
		verify(metadataCache, never()).abandonUpload(vaultPath("x.txt"))
	}

	@Test
	fun `a download is based on the version it downloaded, also if the cache knows an older one`() {
		val stored = givenStoredCopies()
		givenDocument(lastModified = 5_000)
		givenCloudContent("newer content", modified = 9_000)
		val cache = cache()

		val written = cache.fileForWriting(vaultPath("x.txt"), keepContent = true)
		cache.closedForWriting(written, null, changed = true) { vaultPath("x.txt") }

		assertEquals(9_000L, stored.getValue("x.txt").lastModified)
		val baseLastModified = argumentCaptor<(VaultPath) -> Long?>()
		verify(metadataCache).upload(eq(vaultPath("x.txt")), eq(written), baseLastModified.capture(), any(), anyOrNull(), any(), any())
		assertEquals(9_000L, baseLastModified.firstValue(vaultPath("x.txt")))
	}

	@Test
	fun `a copy written with an error is kept if its record can't be dropped`() {
		val copy = copy("copy", "partly written")
		whenever(dao.loadLocalNames()).thenReturn(listOf("copy"))
		whenever(dao.load(VAULT_ID, "x.txt")).thenReturn(cachedFile("copy"))
		doThrow(IllegalStateException("disk full")).whenever(dao).delete(any(), any())

		assertThrows<IllegalStateException> { cache().discard(vaultPath("x.txt"), copy) }

		assertTrue(copy.exists())
	}

	@Test
	fun `a writer that failed without changing the copy keeps an earlier write waiting for its upload`() {
		val stored = givenStoredCopies(cachedFile("pending"))
		val pending = copy("pending", "earlier write")
		givenDocument(lastModified = 5_000, status = DocumentMetadataEntity.STATUS_UPLOADING)
		val cache = cache()
		val written = cache.fileForWriting(vaultPath("x.txt"), keepContent = true)
		assertEquals(pending, written)

		cache.closedForWriting(written, IOException("Remote side is dead"), changed = false) { vaultPath("x.txt") }

		assertEquals("earlier write", pending.readText())
		assertEquals("pending", stored.getValue("x.txt").localName)
		verify(metadataCache, never()).abandonUpload(any())
	}

	@Test
	fun `a detached writer's copy is uploaded once, also if the close is reported before its watch is kept and again later`() {
		givenDocument(lastModified = 5_000, status = DocumentMetadataEntity.STATUS_CREATING)
		val stopped = mutableListOf<File>()
		var onClose: (() -> Unit)? = null
		val cache = cache { file, onClosed ->
			onClose = onClosed
			onClosed()
			AutoCloseable { stopped.add(file) }
		}
		val written = cache.fileForWriting(vaultPath("x.txt"), keepContent = true)
		whenever(dao.load(VAULT_ID, "x.txt")).thenReturn(cachedFile(written.name))
		val read = cache.file(vaultPath("x.txt"))

		cache.closedForWriting(written, mock<ParcelFileDescriptor.FileDescriptorDetachedException>(), changed = true) { vaultPath("x.txt") }
		onClose!!()

		verify(metadataCache).upload(eq(vaultPath("x.txt")), eq(written), any(), any(), anyOrNull(), any(), any())
		assertEquals(listOf(written), stopped)
		// the reader's claim is still there
		whenever(dao.loadEvictable()).thenReturn(listOf(cachedFile(written.name)))
		assertTrue(written.setLastModified(now))
		cache.evictAll()
		assertTrue(read.exists())
		cache.release(read)
		cache.evictAll()
		assertFalse(read.exists())
	}

	@Test
	fun `a detached writer whose upload can't be started gives up its copy`() {
		givenDocument(lastModified = 5_000, status = DocumentMetadataEntity.STATUS_CREATING)
		val closeWatches = mutableMapOf<File, () -> Unit>()
		val cache = cache { file, onClosed ->
			closeWatches[file] = onClosed
			AutoCloseable { closeWatches.remove(file) }
		}
		val written = cache.fileForWriting(vaultPath("x.txt"), keepContent = true)
		var gone = false

		cache.closedForWriting(written, mock<ParcelFileDescriptor.FileDescriptorDetachedException>(), changed = true) {
			if (gone) throw FileNotFoundException("gone") else vaultPath("x.txt")
		}
		gone = true
		closeWatches.getValue(written)()

		cache.evictAll()
		assertFalse(written.exists())
	}

	@Test
	fun `an import is recorded and uploaded while no change is made to the cache`() {
		val paused = recordWhetherChangesPaused()
		givenImportTarget("x.txt", isNew = false, lastModified = 5_000)
		val cache = cache()

		cache.importFile(VaultPath(vault), "x.txt", replace = true, requireListing = false, progressAware = mock(), modifiedDate = null) { it.write("imported".toByteArray()) }

		assertTrue(paused.size >= 3)
		assertTrue(paused.all { it })
	}

	@Test
	fun `a failed new import is dropped while no change is made to the cache`() {
		givenImportTarget("x.txt", isNew = true)
		val cache = cache()
		val paused = recordWhetherChangesPaused()

		assertThrows<InterruptedIOException> {
			cache.importFile(VaultPath(vault), "x.txt", replace = false, requireListing = false, progressAware = mock(), modifiedDate = null) { throw InterruptedIOException() }
		}

		assertEquals(listOf(true), paused)
	}

	@Test
	fun `a download and a copy to write from scratch are recorded while no change is made to the cache`() {
		givenDocument(lastModified = 9_000)
		givenCloudContent("content")
		val cache = cache()
		val paused = recordWhetherChangesPaused()

		cache.file(vaultPath("x.txt"))
		cache.fileForWriting(vaultPath("x.txt"), keepContent = false)

		assertEquals(listOf(true, true), paused)
	}

	@Test
	fun `the cache is cleared while no change is made to the cache`() {
		copy("old", "")
		whenever(dao.loadLocalNames()).thenReturn(listOf("old"))
		whenever(dao.loadEvictable()).thenReturn(listOf(cachedFile("old", path = "old.txt")))
		val paused = recordWhetherChangesPaused()

		cache().evictAll()

		assertEquals(listOf(true), paused)
	}

	@Test
	fun `pending uploads are resumed and a detached writer's copy is marked while no change is made to the cache`() {
		copy("copy", "local changes")
		whenever(dao.loadLocalNames()).thenReturn(listOf("copy"))
		whenever(dao.load(VAULT_ID, "x.txt")).thenReturn(cachedFile("copy"))
		whenever(metadataCache.pendingUploads(vault)).thenReturn(listOf(vaultPath("x.txt")))
		whenever(metadataCache.isUploading(vaultPath("x.txt"))).thenReturn(true)
		givenDocument(lastModified = 5_000, status = DocumentMetadataEntity.STATUS_UPLOADING)
		val cache = cache()
		val written = cache.fileForWriting(vaultPath("x.txt"), keepContent = true)
		val paused = recordWhetherChangesPaused()

		cache.resumeUploads(vault)
		whenever(metadataCache.isUploading(vaultPath("x.txt"))).thenReturn(false)
		cache.closedForWriting(written, mock<ParcelFileDescriptor.FileDescriptorDetachedException>(), changed = true) { vaultPath("x.txt") }

		assertEquals(listOf(true, true), paused)
	}

	/**
	 * @param depth how many [DocumentMetadataCache.withChangesPaused] calls are running
	 * @return for each change of the copies' records, whether it was made while no change could be made to the cache
	 */
	private fun recordWhetherChangesPaused(depth: AtomicInteger = AtomicInteger()): List<Boolean> {
		whenever(metadataCache.withChangesPaused(any<() -> Any?>())).thenAnswer {
			depth.incrementAndGet()
			try {
				it.getArgument<() -> Any?>(0)()
			} finally {
				depth.decrementAndGet()
			}
		}
		val paused = mutableListOf<Boolean>()
		doAnswer { paused.add(depth.get() > 0) }.whenever(dao).store(any())
		doAnswer { paused.add(depth.get() > 0) }.whenever(dao).delete(any(), any())
		doAnswer { paused.add(depth.get() > 0) }.whenever(metadataCache).markPendingUpload(any())
		doAnswer { paused.add(depth.get() > 0) }.whenever(metadataCache).abandonUpload(any())
		whenever(metadataCache.upload(any(), any(), any(), any(), anyOrNull(), any(), any())).thenAnswer {
			paused.add(depth.get() > 0)
			mock<DocumentMetadataCache.Upload>()
		}
		return paused
	}

	@Test
	fun `the detached writers of a copy share one watch, which takes each close for one of them`() {
		givenDocument(lastModified = 5_000, status = DocumentMetadataEntity.STATUS_CREATING)
		val watches = mutableListOf<() -> Unit>()
		val stopped = mutableListOf<File>()
		val cache = cache { file, onClosed ->
			watches.add(onClosed)
			AutoCloseable { stopped.add(file) }
		}
		val first = cache.fileForWriting(vaultPath("x.txt"), keepContent = true)
		whenever(dao.load(VAULT_ID, "x.txt")).thenReturn(cachedFile(first.name))
		val second = cache.fileForWriting(vaultPath("x.txt"), keepContent = true)
		assertEquals(first, second)

		cache.closedForWriting(first, mock<ParcelFileDescriptor.FileDescriptorDetachedException>(), changed = true) { vaultPath("x.txt") }
		cache.closedForWriting(second, mock<ParcelFileDescriptor.FileDescriptorDetachedException>(), changed = true) { vaultPath("x.txt") }

		assertEquals(1, watches.size)
		watches.single()()
		verify(metadataCache).upload(eq(vaultPath("x.txt")), eq(first), any(), any(), anyOrNull(), any(), any())
		assertTrue(stopped.isEmpty())
		cache.evictAll()
		assertTrue(first.exists())
		watches.single()()
		verify(metadataCache, times(2)).upload(eq(vaultPath("x.txt")), eq(first), any(), any(), anyOrNull(), any(), any())
		assertEquals(listOf(first), stopped)
		cache.evictAll()
		assertFalse(first.exists())
	}

	@Test
	fun `a copy still open for writing stays pending after its upload`() {
		copy("copy", "earlier write")
		whenever(dao.loadLocalNames()).thenReturn(listOf("copy"))
		whenever(dao.load(VAULT_ID, "x.txt")).thenReturn(cachedFile("copy"))
		givenDocument(lastModified = 5_000, status = DocumentMetadataEntity.STATUS_UPLOADING)
		val cache = cache()
		val written = cache.fileForWriting(vaultPath("x.txt"), keepContent = true)
		cache.upload(vaultPath("x.txt"), written)
		val onUploaded = argumentCaptor<DocumentMetadataCache.OnUploaded>()
		verify(metadataCache).upload(eq(vaultPath("x.txt")), eq(written), any(), any(), anyOrNull(), any(), onUploaded.capture())

		onUploaded.firstValue.onUploaded(vaultPath("x.txt"), vaultPath("x.txt"), 9_000L, true)
		verify(metadataCache).markPendingUpload(vaultPath("x.txt"))

		whenever(metadataCache.isUploading(vaultPath("x.txt"))).thenReturn(true)
		onUploaded.firstValue.onUploaded(vaultPath("x.txt"), vaultPath("x.txt"), 9_000L, true)
		whenever(metadataCache.isUploading(vaultPath("x.txt"))).thenReturn(false)
		cache.closedForWriting(written, null, changed = false) { vaultPath("x.txt") }
		onUploaded.firstValue.onUploaded(vaultPath("x.txt"), vaultPath("x.txt"), 9_000L, true)
		// neither while the next upload waits nor once the writer closed
		verify(metadataCache, times(1)).markPendingUpload(vaultPath("x.txt"))
	}

	@Test
	fun `a copy stays pending after its upload until the last of its writers closed it`() {
		copy("copy", "earlier write")
		whenever(dao.loadLocalNames()).thenReturn(listOf("copy"))
		whenever(dao.load(VAULT_ID, "x.txt")).thenReturn(cachedFile("copy"))
		givenDocument(lastModified = 5_000, status = DocumentMetadataEntity.STATUS_UPLOADING)
		val cache = cache()
		val first = cache.fileForWriting(vaultPath("x.txt"), keepContent = true)
		val second = cache.fileForWriting(vaultPath("x.txt"), keepContent = true)
		assertEquals(first, second)
		cache.upload(vaultPath("x.txt"), first)
		val onUploaded = argumentCaptor<DocumentMetadataCache.OnUploaded>()
		verify(metadataCache).upload(eq(vaultPath("x.txt")), eq(first), any(), any(), anyOrNull(), any(), onUploaded.capture())

		cache.closedForWriting(first, null, changed = false) { vaultPath("x.txt") }
		onUploaded.firstValue.onUploaded(vaultPath("x.txt"), vaultPath("x.txt"), 9_000L, true)
		verify(metadataCache).markPendingUpload(vaultPath("x.txt"))

		cache.closedForWriting(second, null, changed = false) { vaultPath("x.txt") }
		onUploaded.firstValue.onUploaded(vaultPath("x.txt"), vaultPath("x.txt"), 9_000L, true)
		verify(metadataCache, times(1)).markPendingUpload(vaultPath("x.txt"))
	}

	@Test
	fun `a copy whose writer closed is not kept pending after its upload, also while a reader still has it`() {
		copy("copy", "earlier write")
		whenever(dao.loadLocalNames()).thenReturn(listOf("copy"))
		whenever(dao.load(VAULT_ID, "x.txt")).thenReturn(cachedFile("copy"))
		givenDocument(lastModified = 5_000, status = DocumentMetadataEntity.STATUS_UPLOADING)
		val cache = cache()
		val read = cache.file(vaultPath("x.txt"))
		val written = cache.fileForWriting(vaultPath("x.txt"), keepContent = true)
		cache.closedForWriting(written, null, changed = false) { vaultPath("x.txt") }
		cache.upload(vaultPath("x.txt"), read)
		val onUploaded = argumentCaptor<DocumentMetadataCache.OnUploaded>()
		verify(metadataCache).upload(eq(vaultPath("x.txt")), eq(read), any(), any(), anyOrNull(), any(), onUploaded.capture())

		onUploaded.firstValue.onUploaded(vaultPath("x.txt"), vaultPath("x.txt"), 9_000L, true)

		verify(dao).updateLastModified(VAULT_ID, "x.txt", 9_000L)
		verify(metadataCache, never()).markPendingUpload(any())
	}

	@Test
	fun `an upload's end is recorded where its file is now, also if it was moved meanwhile`() {
		copy("copy", "local changes")
		whenever(dao.loadLocalNames()).thenReturn(listOf("copy"))
		whenever(dao.load(VAULT_ID, "x.txt")).thenReturn(cachedFile("copy"))
		givenDocument(lastModified = 5_000, status = DocumentMetadataEntity.STATUS_UPLOADING)
		val cache = cache()
		val written = cache.fileForWriting(vaultPath("x.txt"), keepContent = true)
		cache.upload(vaultPath("x.txt"), written)
		val onUploaded = argumentCaptor<DocumentMetadataCache.OnUploaded>()
		verify(metadataCache).upload(eq(vaultPath("x.txt")), eq(written), any(), any(), anyOrNull(), any(), onUploaded.capture())
		whenever(dao.load(VAULT_ID, "x.txt")).thenReturn(null)
		whenever(dao.loadByLocalName("copy")).thenReturn(cachedFile("copy", path = "y.txt"))

		onUploaded.firstValue.onUploaded(vaultPath("x.txt"), vaultPath("x.txt"), 9_000L, true)

		verify(dao).updateLastModified(VAULT_ID, "y.txt", 9_000L)
		verify(metadataCache).markPendingUpload(vaultPath("y.txt"))
	}

	@Test
	fun `a detached writer's close is handled while no change is made to the cache`() {
		givenDocument(lastModified = 5_000, status = DocumentMetadataEntity.STATUS_CREATING)
		val closeWatches = mutableMapOf<File, () -> Unit>()
		val cache = cache { file, onClosed ->
			closeWatches[file] = onClosed
			AutoCloseable { closeWatches.remove(file) }
		}
		val written = cache.fileForWriting(vaultPath("x.txt"), keepContent = true)
		cache.closedForWriting(written, mock<ParcelFileDescriptor.FileDescriptorDetachedException>(), changed = true) { vaultPath("x.txt") }
		val paused = recordWhetherChangesPaused()
		whenever(dao.load(VAULT_ID, "x.txt")).thenReturn(cachedFile("replacement"))

		closeWatches.getValue(written)()

		assertTrue(paused.isNotEmpty())
		assertTrue(paused.all { it })
	}

	@Test
	fun `an upload's end is recorded while no change is made to the cache`() {
		val onUploaded = captureUploadCallback(copy("copy", "local changes"))
		val paused = recordWhetherChangesPaused()

		onUploaded.onUploaded(vaultPath("x.txt"), vaultPath("x (1A2B3).txt"), 9_000L, true)

		assertEquals(listOf(true, true), paused)
	}

	@Test
	fun `a writer whose file is gone when it closes gives up its copy`() {
		givenDocument(lastModified = 5_000, status = DocumentMetadataEntity.STATUS_CREATING)
		val cache = cache()
		val written = cache.fileForWriting(vaultPath("x.txt"), keepContent = false)

		assertThrows<FileNotFoundException> {
			cache.closedForWriting(written, null, changed = true) { throw FileNotFoundException("gone") }
		}

		cache.evictAll()
		assertFalse(written.exists())
	}

	@Test
	fun `canceling an import drops its record but keeps its copy while a reader has it`() {
		val stored = givenStoredCopies(cachedFile("imported"))
		doAnswer { stored.remove(it.getArgument<String>(1)) }.whenever(dao).delete(any(), any())
		val imported = copy("imported", "imported")
		givenDocument(lastModified = 5_000, status = DocumentMetadataEntity.STATUS_UPLOADING)
		val cache = cache()
		val read = cache.file(vaultPath("x.txt"))
		val import = upload(localFile = imported, current = DocumentMetadataEntity().apply { path = "x.txt" })

		cache.cancel(import)
		import.result.completeExceptionally(CancellationException())

		assertNull(stored["x.txt"])
		assertTrue(read.exists())
		cache.release(read)
		cache.evictAll()
		assertFalse(read.exists())
	}

	@Test
	fun `canceling an import whose upload then finishes drops nothing`() {
		val imported = copy("imported", "imported")
		whenever(dao.loadLocalNames()).thenReturn(listOf("imported"))
		whenever(dao.load(VAULT_ID, "x.txt")).thenReturn(cachedFile("imported"))
		val cache = cache()
		val import = upload(localFile = imported, current = DocumentMetadataEntity().apply { path = "x.txt" })

		cache.cancel(import)
		import.result.complete(mock())

		verify(import).stop()
		verify(dao, never()).delete(any(), any())
		verify(metadataCache, never()).abandonUpload(any())
		assertTrue(imported.exists())
	}

	@Test
	fun `opening a file for the app reports the progress of its download`() {
		givenDocument(lastModified = 9_000)
		givenCloudContent("content", modified = 9_000)
		givenDocumentId()
		val progressAware: ProgressAware<DownloadState> = mock()

		cache().prepare(vaultPath("x.txt"), progressAware)

		verify(repository).read(any(), any(), eq(progressAware))
	}

	@Test
	fun `the copies the app opens are not kept in use`() {
		givenStoredCopies(cachedFile("copy", lastModified = 5_000))
		val copy = copy("copy", "content")
		givenDocument(lastModified = 5_000)
		givenDocumentId()
		whenever(dao.loadEvictable()).thenReturn(listOf(cachedFile("copy", lastModified = 5_000)))
		val cache = cache()

		cache.prepare(vaultPath("x.txt"), ProgressAware.NO_OP_PROGRESS_AWARE_DOWNLOAD)
		cache.openForExternalEditor()
		cache.unpinBase("1:7")
		cache.evictAll()

		assertFalse(copy.exists())
	}

	@Test
	fun `a file opened for an external editor keeps the opened version as the base of its writes, also once the cloud has a newer one`() {
		givenStoredCopies(cachedFile("copy", lastModified = 5_000))
		copy("copy", "opened content")
		givenDocument(lastModified = 5_000)
		givenDocumentId()
		val cache = cache()
		cache.openForExternalEditor()
		givenDocument(lastModified = 9_000)
		givenCloudContent("newer content", modified = 9_000)

		val written = cache.fileForWriting(vaultPath("x.txt"), keepContent = true)
		written.writeText("edited")
		cache.closedForWriting(written, null, changed = true) { vaultPath("x.txt") }

		assertEquals("copy", written.name)
		val baseLastModified = argumentCaptor<(VaultPath) -> Long?>()
		verify(metadataCache).upload(eq(vaultPath("x.txt")), eq(written), baseLastModified.capture(), any(), anyOrNull(), any(), any())
		assertEquals(5_000L, baseLastModified.firstValue(vaultPath("x.txt")))
		verify(repository, never()).read(any(), any(), any())
	}

	@Test
	fun `a file opened for an external editor that overwrites it keeps the opened version as base`() {
		val stored = givenStoredCopies(cachedFile("copy", lastModified = 5_000))
		copy("copy", "opened content")
		givenDocument(lastModified = 5_000)
		givenDocumentId()
		val cache = cache()
		cache.openForExternalEditor()
		givenDocument(lastModified = 9_000)

		cache.fileForWriting(vaultPath("x.txt"), keepContent = false)

		assertEquals(5_000L, stored.getValue("x.txt").lastModified)
	}

	@Test
	fun `once the external editor is done, writes are based on the cloud's current version again`() {
		givenStoredCopies(cachedFile("copy", lastModified = 5_000))
		copy("copy", "opened content")
		givenDocument(lastModified = 5_000)
		givenDocumentId()
		val cache = cache()
		cache.openForExternalEditor()
		cache.unpinBase("1:7")
		givenDocument(lastModified = 9_000)
		givenCloudContent("newer content", modified = 9_000)

		val written = cache.fileForWriting(vaultPath("x.txt"), keepContent = true)

		assertEquals("newer content", written.readText())
	}

	@Test
	fun `a file opened for an external editor can be written while its vault is locked and is uploaded once it is unlocked`() {
		givenStoredCopies(cachedFile("copy", lastModified = 5_000))
		copy("copy", "opened content")
		givenDocument(lastModified = 5_000)
		givenDocumentId()
		val cache = cache()
		cache.openForExternalEditor()
		val locked = VaultPath(Vault.aCopyOf(vault).withUnlocked(false).build(), "x.txt")

		val opened = requireNotNull(cache.pinnedFile(locked))
		val written = cache.fileForWriting(locked, keepContent = true)
		cache.release(opened)
		written.writeText("edited while locked")
		cache.closedForWriting(written, null, changed = true) { locked }

		verify(metadataCache).markPendingUpload(vaultPath("x.txt"))
		verify(metadataCache, never()).upload(any(), any(), any(), any(), anyOrNull(), any(), any())
		verify(repository, never()).read(any(), any(), any())

		whenever(metadataCache.pendingUploads(vault)).thenReturn(listOf(vaultPath("x.txt")))
		whenever(metadataCache.isUploading(vaultPath("x.txt"))).thenReturn(true)
		cache.resumeUploads(vault)

		verify(metadataCache).upload(eq(vaultPath("x.txt")), eq(written), any(), any(), anyOrNull(), any(), any())
		assertEquals("edited while locked", written.readText())
	}

	@Test
	fun `only a file opened for an external editor and not yet done with can be opened while its vault is locked`() {
		givenStoredCopies(cachedFile("copy", lastModified = 5_000))
		copy("copy", "opened content")
		givenDocument(lastModified = 5_000)
		givenDocumentId()
		val cache = cache()
		val locked = VaultPath(Vault.aCopyOf(vault).withUnlocked(false).build(), "x.txt")

		cache.prepare(vaultPath("x.txt"), ProgressAware.NO_OP_PROGRESS_AWARE_DOWNLOAD)
		assertNull(cache.pinnedFile(locked))
		cache.openForExternalEditor()
		assertNotNull(cache.pinnedFile(locked)?.also(cache::release))
		cache.unpinBase("1:7")
		assertNull(cache.pinnedFile(locked))
	}

	@Test
	fun `an external editor's save stays based on the opened version also if a reader refreshed the copy meanwhile`() {
		givenStoredCopies(cachedFile("copy", lastModified = 5_000))
		copy("copy", "opened content")
		givenDocument(lastModified = 5_000)
		givenDocumentId()
		val cache = cache()
		cache.openForExternalEditor()
		givenDocument(lastModified = 9_000)
		givenCloudContent("newer content", modified = 9_000)
		cache.release(cache.file(vaultPath("x.txt")))

		val written = cache.fileForWriting(vaultPath("x.txt"), keepContent = true)
		written.writeText("edited")
		cache.closedForWriting(written, null, changed = true) { vaultPath("x.txt") }

		val baseLastModified = argumentCaptor<(VaultPath) -> Long?>()
		verify(metadataCache).upload(eq(vaultPath("x.txt")), eq(written), baseLastModified.capture(), any(), anyOrNull(), any(), any())
		assertEquals(5_000L, baseLastModified.firstValue(vaultPath("x.txt")))
	}

	@Test
	fun `the base an external editor's writes keep moves on with each upload of its saves`() {
		val stored = givenStoredCopies(cachedFile("copy", lastModified = 5_000))
		copy("copy", "opened content")
		givenDocument(lastModified = 5_000)
		givenDocumentId()
		val cache = cache()
		cache.openForExternalEditor()
		val written = cache.fileForWriting(vaultPath("x.txt"), keepContent = true)
		written.writeText("saved")
		cache.closedForWriting(written, null, changed = true) { vaultPath("x.txt") }
		val uploaded = argumentCaptor<DocumentMetadataCache.OnUploaded>()
		verify(metadataCache).upload(eq(vaultPath("x.txt")), eq(written), any(), any(), anyOrNull(), any(), uploaded.capture())

		uploaded.firstValue.onUploaded(vaultPath("x.txt"), vaultPath("x.txt"), 6_000L, true)
		cache.fileForWriting(vaultPath("x.txt"), keepContent = false)

		assertEquals(6_000L, stored.getValue("x.txt").lastModified)
	}

	@Test
	fun `an upload the external editor didn't write, e g an import, doesn't move the base its writes keep`() {
		val stored = givenStoredCopies(cachedFile("copy", lastModified = 5_000))
		val copy = copy("copy", "opened content")
		givenDocument(lastModified = 5_000)
		givenDocumentId()
		val cache = cache()
		cache.openForExternalEditor()
		cache.upload(vaultPath("x.txt"), copy)
		val uploaded = argumentCaptor<DocumentMetadataCache.OnUploaded>()
		verify(metadataCache).upload(eq(vaultPath("x.txt")), eq(copy), any(), any(), anyOrNull(), any(), uploaded.capture())

		uploaded.firstValue.onUploaded(vaultPath("x.txt"), vaultPath("x.txt"), 6_000L, true)
		cache.fileForWriting(vaultPath("x.txt"), keepContent = false)

		assertEquals(5_000L, stored.getValue("x.txt").lastModified)
	}

	@Test
	fun `the copy of a file an external editor may write is kept when the cache is cleared, until editing ends`() {
		givenStoredCopies(cachedFile("copy", lastModified = 5_000))
		givenDocument(lastModified = 5_000)
		givenDocumentId()
		whenever(dao.loadEvictable()).thenReturn(listOf(cachedFile("copy", lastModified = 5_000)))
		val cache = cache()
		val copy = copy("copy", "opened content")
		cache.openForExternalEditor()

		cache.evictAll()
		assertTrue(copy.exists())

		cache.unpinBase("1:7")
		cache.evictAll()
		assertFalse(copy.exists())
	}

	@Test
	fun `a file opened for an external editor without a recorded copy keeps the version the cache lists as base`() {
		val stored = givenStoredCopies()
		givenDocument(lastModified = 5_000)
		givenDocumentId()
		whenever(metadataCache.cachedDocument(vaultPath("x.txt"))).thenReturn(DocumentMetadataEntity().apply { lastModified = 4_000 })
		val cache = cache()

		cache.pinBase("1:7")
		cache.fileForWriting(vaultPath("x.txt"), keepContent = false)

		assertEquals(4_000L, stored.getValue("x.txt").lastModified)
	}

	@Test
	fun `the cache is cleared also if a document whose base is pinned is gone`() {
		givenStoredCopies(cachedFile("copy", lastModified = 5_000))
		givenDocument(lastModified = 5_000)
		givenDocumentId()
		whenever(dao.loadEvictable()).thenReturn(listOf(cachedFile("copy", lastModified = 5_000)))
		val cache = cache()
		val copy = copy("copy", "opened content")
		cache.openForExternalEditor()
		whenever(metadataCache.path("1:7")).thenAnswer { throw FileNotFoundException("Unknown document: 1:7") }

		cache.evictAll()

		assertFalse(copy.exists())
	}

	@Test
	fun `a save while the vault is locked stays pending also if an earlier upload of the copy ends meanwhile`() {
		givenStoredCopies(cachedFile("copy", lastModified = 5_000))
		copy("copy", "opened content")
		givenDocument(lastModified = 5_000)
		givenDocumentId()
		val cache = cache()
		cache.openForExternalEditor()
		// e.g. the editor's save from before the vault locked
		whenever(metadataCache.isUploading(vaultPath("x.txt"))).thenReturn(true)
		val locked = VaultPath(Vault.aCopyOf(vault).withUnlocked(false).build(), "x.txt")

		val opened = requireNotNull(cache.pinnedFile(locked))
		val written = cache.fileForWriting(locked, keepContent = true)
		cache.release(opened)
		written.writeText("edited while locked")
		cache.closedForWriting(written, null, changed = true) { locked }

		// a new upload generation, which the earlier upload's end leaves pending
		verify(metadataCache).markPendingUpload(vaultPath("x.txt"))
		verify(metadataCache, never()).upload(any(), any(), any(), any(), anyOrNull(), any(), any())
	}

	@Test
	fun `the base an external editor's writes keep moves on with each upload of its overlapping saves`() {
		val stored = givenStoredCopies(cachedFile("copy", lastModified = 5_000))
		copy("copy", "opened content")
		givenDocument(lastModified = 5_000)
		givenDocumentId()
		val cache = cache()
		cache.openForExternalEditor()
		val written = cache.fileForWriting(vaultPath("x.txt"), keepContent = true)
		written.writeText("saved")
		cache.closedForWriting(written, null, changed = true) { vaultPath("x.txt") }
		whenever(metadataCache.isUploading(vaultPath("x.txt"))).thenReturn(true)
		val again = cache.fileForWriting(vaultPath("x.txt"), keepContent = true)
		again.writeText("saved again")
		cache.closedForWriting(again, null, changed = true) { vaultPath("x.txt") }
		val uploaded = argumentCaptor<DocumentMetadataCache.OnUploaded>()
		verify(metadataCache, times(2)).upload(eq(vaultPath("x.txt")), eq(written), any(), any(), anyOrNull(), any(), uploaded.capture())

		uploaded.firstValue.onUploaded(vaultPath("x.txt"), vaultPath("x.txt"), 6_000L, false)
		uploaded.secondValue.onUploaded(vaultPath("x.txt"), vaultPath("x.txt"), 7_000L, true)
		cache.fileForWriting(vaultPath("x.txt"), keepContent = false)

		assertEquals(7_000L, stored.getValue("x.txt").lastModified)
	}

	@Test
	fun `reopening a file for an external editor while its earlier save uploads moves the base on with that upload`() {
		val stored = givenStoredCopies(cachedFile("copy", lastModified = 5_000))
		copy("copy", "opened content")
		givenDocument(lastModified = 5_000)
		givenDocumentId()
		val cache = cache()
		cache.openForExternalEditor()
		val written = cache.fileForWriting(vaultPath("x.txt"), keepContent = true)
		written.writeText("saved")
		cache.closedForWriting(written, null, changed = true) { vaultPath("x.txt") }
		val uploaded = argumentCaptor<DocumentMetadataCache.OnUploaded>()
		verify(metadataCache).upload(eq(vaultPath("x.txt")), eq(written), any(), any(), anyOrNull(), any(), uploaded.capture())
		cache.unpinBase("1:7")
		whenever(metadataCache.isUploading(vaultPath("x.txt"))).thenReturn(true)
		whenever(metadataCache.hasUploadNotEnded(vaultPath("x.txt"))).thenReturn(true)
		givenDocument(lastModified = 5_000, status = DocumentMetadataEntity.STATUS_UPLOADING)

		cache.openForExternalEditor()
		uploaded.firstValue.onUploaded(vaultPath("x.txt"), vaultPath("x.txt"), 6_000L, true)
		cache.fileForWriting(vaultPath("x.txt"), keepContent = false)

		assertEquals(6_000L, stored.getValue("x.txt").lastModified)
	}

	@Test
	fun `reopening a file for an external editor while the end of its earlier save's upload is recorded moves the base on with it`() {
		val stored = givenStoredCopies(cachedFile("copy", lastModified = 5_000))
		copy("copy", "opened content")
		givenDocument(lastModified = 5_000)
		givenDocumentId()
		val cache = cache()
		val uploaded = savedByExternalEditor(cache)
		whenever(metadataCache.isUploading(vaultPath("x.txt"))).thenReturn(true)
		givenDocument(lastModified = 5_000, status = DocumentMetadataEntity.STATUS_UPLOADING)
		cache.prepare(vaultPath("x.txt"), ProgressAware.NO_OP_PROGRESS_AWARE_DOWNLOAD)
		// the file is synced, its copy isn't updated yet
		whenever(metadataCache.isUploading(vaultPath("x.txt"))).thenReturn(false)
		whenever(metadataCache.hasUploadNotEnded(vaultPath("x.txt"))).thenReturn(true)

		cache.pinBase("1:7")
		uploaded.onUploaded(vaultPath("x.txt"), vaultPath("x.txt"), 6_000L, true)
		cache.fileForWriting(vaultPath("x.txt"), keepContent = false)

		assertEquals(6_000L, stored.getValue("x.txt").lastModified)
	}

	@Test
	@Timeout(10)
	fun `an upload's end recorded for the copy while its base is pinned moves the pin on`() {
		val stored = givenStoredCopies(cachedFile("copy", lastModified = 5_000))
		copy("copy", "opened content")
		givenDocument(lastModified = 5_000)
		givenDocumentId()
		val cache = cache()
		val uploaded = savedByExternalEditor(cache)
		whenever(metadataCache.isUploading(vaultPath("x.txt"))).thenReturn(true)
		whenever(metadataCache.hasUploadNotEnded(vaultPath("x.txt"))).thenReturn(true)
		givenDocument(lastModified = 5_000, status = DocumentMetadataEntity.STATUS_UPLOADING)
		cache.prepare(vaultPath("x.txt"), ProgressAware.NO_OP_PROGRESS_AWARE_DOWNLOAD)
		// the upload's end is recorded for the copy right after the pin read the copy's version
		var ending: Thread? = null
		whenever(dao.load(eq(VAULT_ID), any())).thenAnswer { invocation ->
			val copy = stored[invocation.getArgument(1)]
			if (ending == null) {
				ending = thread { uploaded.onUploaded(vaultPath("x.txt"), vaultPath("x.txt"), 6_000L, true) }.also(::awaitEndOrLock)
			}
			copy
		}

		cache.pinBase("1:7")
		requireNotNull(ending).join()
		cache.fileForWriting(vaultPath("x.txt"), keepContent = false)

		assertEquals(6_000L, stored.getValue("x.txt").lastModified)
	}

	/**
	 * Waits until [thread] has ended or waits for a lock, e.g. one the current thread holds.
	 */
	private fun awaitEndOrLock(thread: Thread) {
		while (thread.state != Thread.State.TERMINATED && thread.state != Thread.State.BLOCKED) {
			Thread.sleep(1)
		}
	}

	/**
	 * Opens x.txt for an external editor, which saves it, and ends editing while the save is uploaded.
	 *
	 * @return the upload's callback for its end
	 */
	private fun savedByExternalEditor(cache: DocumentContentCache): DocumentMetadataCache.OnUploaded {
		cache.openForExternalEditor()
		val written = cache.fileForWriting(vaultPath("x.txt"), keepContent = true)
		written.writeText("saved")
		cache.closedForWriting(written, null, changed = true) { vaultPath("x.txt") }
		val uploaded = argumentCaptor<DocumentMetadataCache.OnUploaded>()
		verify(metadataCache).upload(eq(vaultPath("x.txt")), eq(written), any(), any(), anyOrNull(), any(), uploaded.capture())
		cache.unpinBase("1:7")
		return uploaded.firstValue
	}

	@Test
	fun `a copy is current for making a thumbnail only if it is the cloud's version or waits for its upload`() {
		givenStoredCopies(cachedFile("copy", lastModified = 5_000))
		val copy = copy("copy", "content")
		val cache = cache()
		val metadata = { lastModified: Long, status: String -> DocumentMetadataEntity().apply { path = "x.txt"; this.lastModified = lastModified; this.status = status } }

		assertEquals(copy, cache.currentCopy(vaultPath("x.txt"), metadata(5_400, DocumentMetadataEntity.STATUS_SYNCED))?.also(cache::release))
		assertNull(cache.currentCopy(vaultPath("x.txt"), metadata(9_000, DocumentMetadataEntity.STATUS_SYNCED)))
		assertEquals(copy, cache.currentCopy(vaultPath("x.txt"), metadata(9_000, DocumentMetadataEntity.STATUS_UPLOADING))?.also(cache::release))
	}

	@Test
	fun `a retry resumes only the uploads asked for, reporting their failures, once the interrupted moves are resolved`() {
		givenStoredCopies(cachedFile("x", path = "x.txt"), cachedFile("y", path = "y.txt"))
		whenever(metadataCache.pendingUploads(vault)).thenReturn(listOf(vaultPath("x.txt"), vaultPath("y.txt")))
		whenever(metadataCache.isUploading(any())).thenReturn(true)
		val cache = cache()

		cache.resumeUploads(vault, listOf(vaultPath("x.txt")), userInitiated = true)

		inOrder(metadataCache) {
			verify(metadataCache).resolveInterruptedMoves(vault)
			verify(metadataCache).upload(eq(vaultPath("x.txt")), eq(File(directory, "x")), any(), any(), anyOrNull(), eq(true), any())
		}
		verify(metadataCache, never()).upload(eq(vaultPath("y.txt")), any(), any(), any(), anyOrNull(), any(), any())
	}

	@Test
	fun `the uploads an interrupted move held are resumed once it is resolved`() {
		val resolved = argumentCaptor<(Vault) -> Unit>()
		cache()
		verify(metadataCache).onInterruptedMovesResolved = resolved.capture()
		whenever(metadataCache.pendingUploads(vault)).thenReturn(emptyList())

		resolved.firstValue(vault)

		verify(metadataCache).pendingUploads(vault)
	}

	@Test
	fun `a deleted node's thumbnails and those of its descendants are deleted once the cloud deleted it`() {
		val thumbnails: ThumbnailStore = mock()
		val deleted = CompletableFuture<Unit>()
		givenDocumentId()
		whenever(metadataCache.delete(vaultPath("x.txt"), false)).thenReturn(DocumentMetadataCache.Deletion(listOf("1:8"), deleted))
		val cache = cache(thumbnails = thumbnails)
		clearInvocations(thumbnails)

		cache.delete(vaultPath("x.txt"))
		verifyNoInteractions(thumbnails)
		deleted.complete(Unit)

		verify(thumbnails).delete(listOf("1:7", "1:8"))
		verifyNoMoreInteractions(thumbnails)
	}

	@Test
	fun `a rejected deletion keeps the thumbnails`() {
		val thumbnails: ThumbnailStore = mock()
		val deleted = CompletableFuture<Unit>()
		givenDocumentId()
		whenever(metadataCache.delete(vaultPath("x.txt"), false)).thenReturn(DocumentMetadataCache.Deletion(emptyList(), deleted))
		val cache = cache(thumbnails = thumbnails)
		clearInvocations(thumbnails)

		cache.delete(vaultPath("x.txt"))
		deleted.completeExceptionally(NetworkConnectionException())

		verifyNoInteractions(thumbnails)
	}

	@Test
	fun `outdated thumbnails are evicted with the copies, and clearing the cache deletes all of them`() {
		val thumbnails: ThumbnailStore = mock()
		val cache = cache(thumbnails = thumbnails)
		verify(thumbnails).evict()

		cache.evict()
		cache.evictAll()

		verify(thumbnails, times(2)).evict()
		verify(thumbnails).clear()
	}

	private fun captureUploadCallback(copy: File): DocumentMetadataCache.OnUploaded {
		whenever(dao.loadLocalNames()).thenReturn(listOf(copy.name))
		whenever(dao.load(VAULT_ID, "x.txt")).thenReturn(cachedFile(copy.name))
		cache().upload(vaultPath("x.txt"), copy)
		val callback = argumentCaptor<DocumentMetadataCache.OnUploaded>()
		verify(metadataCache).upload(eq(vaultPath("x.txt")), eq(copy), any(), any(), anyOrNull(), any(), callback.capture())
		return callback.firstValue
	}

	private fun upload(localFile: File, current: DocumentMetadataEntity?): DocumentMetadataCache.Upload {
		val result = CompletableFuture<VaultDocumentChangeResult<CloudFile>>()
		return mock {
			on { this.result } doReturn result
			on { this.localFile } doReturn localFile
			on { vault } doReturn vault
			on { current() } doReturn current
		}
	}

	private fun givenImportTarget(path: String, isNew: Boolean, lastModified: Long? = null, currentPath: String = path) {
		whenever(metadataCache.createFile(eq(vaultPath(path)), any())).thenReturn(DocumentMetadataCache.FileTarget(7L, isNew))
		whenever(metadataCache.visibleDocument(7L)).thenReturn(DocumentMetadataEntity().apply {
			id = 7L
			vaultId = VAULT_ID
			this.path = currentPath
			parentPath = ""
			name = currentPath
			mimeType = "text/plain"
			this.lastModified = lastModified
			status = DocumentMetadataEntity.STATUS_UPLOADING
		})
	}

	private fun cache(thumbnails: ThumbnailStore? = null, watchClose: (File, () -> Unit) -> AutoCloseable = { _, _ -> AutoCloseable {} }): DocumentContentCache {
		val direct = Executor { it.run() }
		return DocumentContentCache(dao, metadataCache, CloudTaskScheduler(direct, direct, direct), repository, directory, thumbnails, watchClose)
	}

	private fun givenDocumentId() {
		whenever(metadataCache.documentId(vaultPath("x.txt"))).thenReturn("1:7")
		whenever(metadataCache.path("1:7")).thenReturn(vaultPath("x.txt"))
	}

	/**
	 * Opens x.txt like the app does for an external editor.
	 */
	private fun DocumentContentCache.openForExternalEditor() {
		prepare(vaultPath("x.txt"), ProgressAware.NO_OP_PROGRESS_AWARE_DOWNLOAD)
		pinBase("1:7")
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

	private fun givenCloudContent(content: String, modified: Long? = null, whileReading: () -> Unit = {}) {
		val handle: CloudFile = mock()
		val stored: CloudFile = mock { on { this.modified } doReturn modified?.let(::Date) }
		whenever(repository.file(eq(root), any())).thenReturn(handle)
		whenever(repository.metadata(handle)).thenReturn(stored)
		whenever(repository.read(eq(stored), any(), any())).thenAnswer {
			whileReading()
			it.getArgument<OutputStream>(1).write(content.toByteArray())
		}
	}

	/**
	 * @return the recorded copies by path, updated whenever the cache stores one
	 */
	private fun givenStoredCopies(vararg copies: CachedFileEntity): MutableMap<String, CachedFileEntity> {
		val stored = copies.associateBy { it.path }.toMutableMap()
		doAnswer { it.getArgument<CachedFileEntity>(0).let { entity -> stored[entity.path] = entity } }.whenever(dao).store(any())
		whenever(dao.load(eq(VAULT_ID), any())).thenAnswer { stored[it.getArgument(1)] }
		whenever(dao.loadByLocalName(any())).thenAnswer { invocation -> stored.values.find { it.localName == invocation.getArgument(0) } }
		whenever(dao.loadLocalNames()).thenAnswer { stored.values.map { it.localName } }
		return stored
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
