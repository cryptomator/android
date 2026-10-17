package org.cryptomator.data.documents

import android.content.Context
import android.provider.DocumentsContract
import org.cryptomator.data.cloud.crypto.CryptoCloud
import org.cryptomator.data.cloud.crypto.CryptoFile
import org.cryptomator.data.cloud.crypto.RootCryptoFolder
import org.cryptomator.data.db.entities.DocumentMetadataEntity
import org.cryptomator.domain.CachedVaultFile
import org.cryptomator.domain.CachedVaultFolder
import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.CloudFolder
import org.cryptomator.domain.CloudNode
import org.cryptomator.domain.CloudType
import org.cryptomator.domain.Vault
import org.cryptomator.domain.VaultPath
import org.cryptomator.domain.exception.CancellationException
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.domain.exception.IllegalFileNameException
import org.cryptomator.domain.exception.NoSuchCloudFileException
import org.cryptomator.domain.repository.CloudRepository
import org.cryptomator.domain.repository.VaultDocumentChangeResult
import org.cryptomator.domain.usecases.ProgressAware
import org.cryptomator.domain.usecases.cloud.DataSource
import org.cryptomator.domain.usecases.cloud.DownloadState
import org.cryptomator.domain.usecases.cloud.UploadState
import org.cryptomator.util.Optional
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Date
import java.util.concurrent.CompletableFuture

class VaultDocumentRepositoryImplTest {

	private val vault = Vault.aVault().withId(1L).withName("vault").withPath("/vault").withCloudType(CloudType.LOCAL).withPosition(0).withUnlocked(true).build()
	private val context: Context = mock()
	private val metadataCache: DocumentMetadataCache = mock()
	private val contentCache: DocumentContentCache = mock()
	private var cachesCreated = 0
	private val thumbnailStore: ThumbnailStore = mock()
	private val thumbnailGenerator: ThumbnailGenerator = mock()
	private val events = VaultDocumentEvents()
	private val cloudRepository: CloudRepository = mock()
	private val cryptoCloud: CryptoCloud = mock { on { vault } doReturn vault }
	private val inTest = VaultDocumentRepositoryImpl(context, { cachesCreated++; metadataCache }, { cachesCreated++; contentCache }, { thumbnailStore }, { thumbnailGenerator }, events, cloudRepository)

	@Test
	fun `nodes of a vault are found by their cleartext path, other nodes are not in a vault`() {
		val cryptoCloud: CryptoCloud = mock { on { vault } doReturn vault }
		val file = CryptoFile(RootCryptoFolder(cryptoCloud), "x.txt", "/a/x.txt", 1, mock())
		val plaintextFile: CloudFile = mock { on { path } doReturn "/a/x.txt" }

		assertEquals(VaultPath(vault, "a/x.txt"), inTest.pathOf(file))
		assertNull(inTest.pathOf(plaintextFile))
		assertEquals(0, cachesCreated)
	}

	@Test
	fun `a folder is created unless its name is taken in the cache`() {
		val created: CloudFolder = mock()
		whenever(metadataCache.create(any(), any(), any(), any(), any())).thenReturn(DocumentMetadataCache.Creation("1:7", CompletableFuture.completedFuture(VaultDocumentChangeResult<CloudNode>(path("a/new (1A2B3)"), created))))

		val result = inTest.createFolder(path("a"), "new", userInitiated = true).await()

		verify(metadataCache).create(path("a"), "new", true, true, true)
		assertEquals(path("a/new (1A2B3)"), result.path)
		assertSame(created, result.node)
	}

	@Test
	fun `a node that vanished from the cache meanwhile counts as gone from the cloud`() {
		whenever(metadataCache.move(any(), any(), any(), any(), any())).thenAnswer { throw FileNotFoundException("Unknown document: 1:7") }

		assertThrows<NoSuchCloudFileException> { inTest.move(path("a/x.txt"), path("b"), userInitiated = false) }
	}

	@Test
	fun `a node the cache has never listed is looked up before it is renamed, moved or deleted`() {
		whenever(metadataCache.move(any(), any(), any(), any(), any())).thenReturn(CompletableFuture())
		whenever(contentCache.delete(any(), any())).thenReturn(DocumentMetadataCache.Deletion(emptyList(), CompletableFuture()))

		inTest.rename(path("a/x.txt"), "y.txt", userInitiated = false)
		inTest.move(path("a/x.txt"), path("b"), userInitiated = false)
		inTest.delete(path("a/x.txt"), userInitiated = true)

		inOrder(metadataCache, contentCache) {
			verify(metadataCache).requireKnownDocument(path("a/x.txt"))
			verify(metadataCache).move(path("a/x.txt"), path("a"), "y.txt", true, false)
			verify(metadataCache).requireKnownDocument(path("a/x.txt"))
			verify(metadataCache).move(path("a/x.txt"), path("b"), "x.txt", true, false)
			verify(metadataCache).requireKnownDocument(path("a/x.txt"))
			verify(contentCache).delete(path("a/x.txt"), true)
		}
	}

	@Test
	fun `a node the cloud no longer has is not changed`() {
		whenever(metadataCache.requireKnownDocument(path("a/x.txt"))).thenAnswer { throw NoSuchCloudFileException("1:7") }

		assertThrows<NoSuchCloudFileException> { inTest.delete(path("a/x.txt"), userInitiated = false) }
		verifyNoInteractions(contentCache)
	}

	@Test
	fun `an import keeps the source's modification date and reports where the file was uploaded to`() {
		val result = CompletableFuture<VaultDocumentChangeResult<CloudFile>>()
		val uploaded: CloudFile = mock()
		val write = givenImport(result)

		val vaultImport = inTest.importFile(path("a"), "x.txt", dataSource { "content".byteInputStream() }, replace = true, requireListing = true, userInitiated = false, progressAware = mock())
		val copy = java.io.ByteArrayOutputStream()
		write.firstValue.invoke(copy)
		result.complete(VaultDocumentChangeResult(path("a/x (1A2B3).txt"), uploaded))

		verify(contentCache).importFile(eq(path("a")), eq("x.txt"), eq(true), eq(true), eq(false), any(), eq(4_000L), any())
		assertEquals("content", copy.toString())
		assertEquals(path("a/x (1A2B3).txt"), vaultImport.await().path)
		assertSame(uploaded, vaultImport.await().node)
	}

	@Test
	fun `an import that was stopped ends like a canceled use case`() {
		val result = CompletableFuture<VaultDocumentChangeResult<CloudFile>>()
		givenImport(result)
		val vaultImport = inTest.importFile(path("a"), "x.txt", dataSource { "content".byteInputStream() }, replace = false, requireListing = false, userInitiated = false, progressAware = mock())

		vaultImport.cancel()
		result.completeExceptionally(java.util.concurrent.CancellationException("stopped"))

		verify(contentCache).cancel(any())
		assertThrows<CancellationException> { vaultImport.await() }
	}

	@Test
	fun `an import whose source can't be read fails like a failed upload outside vaults`() {
		val write = givenImport(CompletableFuture())
		inTest.importFile(path("a"), "x.txt", dataSource { throw IOException("source gone") }, replace = false, requireListing = false, userInitiated = false, progressAware = mock())

		val e = assertThrows<FatalBackendException> { write.firstValue.invoke(java.io.ByteArrayOutputStream()) }
		assertInstanceOf(IOException::class.java, e.cause)
	}

	@Test
	fun `a name another app chose is not taken for a path`() {
		assertThrows<IllegalFileNameException> {
			inTest.importFile(path("a"), "Work/payroll.pdf", dataSource { "content".byteInputStream() }, replace = false, requireListing = false, userInitiated = false, progressAware = mock())
		}
		verify(contentCache, never()).importFile(any(), any(), any(), any(), any(), any<ProgressAware<UploadState>>(), anyOrNull(), any())
	}

	@Test
	fun `a file is opened for the app once its copy is current, and is known by its documentId`() {
		val progressAware: ProgressAware<DownloadState> = mock()
		whenever(metadataCache.documentId(path("a/x.txt"))).thenReturn("1:7")

		assertEquals("1:7", inTest.open(path("a/x.txt"), progressAware))

		inOrder(metadataCache, contentCache) {
			verify(metadataCache).requireKnownDocument(path("a/x.txt"))
			verify(contentCache).prepare(path("a/x.txt"), progressAware)
		}
	}

	@Test
	fun `a file is read from its copy, which is released also if reading fails`(@TempDir directory: File) {
		val progressAware: ProgressAware<DownloadState> = mock()
		val copy = File(directory, "copy").apply { writeText("content") }
		whenever(contentCache.file(eq(path("a/x.txt")), anyOrNull(), eq(progressAware))).thenReturn(copy)

		val target = java.io.ByteArrayOutputStream()
		inTest.read(path("a/x.txt"), target, progressAware)
		val failing: OutputStream = mock { on { write(any<ByteArray>(), any(), any()) } doThrow IOException("target gone") }
		assertThrows<FatalBackendException> { inTest.read(path("a/x.txt"), failing, progressAware) }

		assertEquals("content", target.toString())
		verify(metadataCache, times(2)).requireKnownDocument(path("a/x.txt"))
		verify(contentCache, times(2)).release(copy)
	}

	@Test
	fun `external editing pins the file's base until it ends`() {
		inTest.startExternalEditing("1:7")
		inTest.endExternalEditing("1:7")

		inOrder(contentCache) {
			verify(contentCache).pinBase("1:7")
			verify(contentCache).unpinBase("1:7")
		}
	}

	@Test
	fun `a folder is listed from the cache with its files' upload states and thumbnails`(@TempDir directory: File) {
		val thumbnail = File(directory, "thumbnail.jpg")
		val authError = Exception("login expired")
		val synced = entity("a/x.jpg", mimeType = "image/jpeg", lastModified = 4_000)
		val uploading = entity("a/y.txt", status = DocumentMetadataEntity.STATUS_UPLOADING)
		val failed = entity("a/z.txt", status = DocumentMetadataEntity.STATUS_UPLOADING)
		val folder = entity("a/b", mimeType = DocumentsContract.Document.MIME_TYPE_DIR)
		whenever(metadataCache.children(path("a"))).thenReturn(DocumentMetadataCache.Children(listOf(synced, uploading, failed, folder), isLoading = true, error = null, authError = authError))
		whenever(metadataCache.uploadFailure(failed)).thenReturn(IOException("upload failed"))
		// only images have thumbnails
		whenever(thumbnailStore.thumbnail(any())).thenReturn(thumbnail)

		val listing = inTest.list(CachedVaultFolder(cryptoCloud, "/a"), forceRefresh = false)

		verify(metadataCache).ensureFolder(path("a"))
		verify(metadataCache, never()).listNow(any())
		assertEquals(listOf("/a/x.jpg", "/a/y.txt", "/a/z.txt", "/a/b"), listing.nodes.map { it.path })
		val files = listing.nodes.filterIsInstance<CachedVaultFile>()
		assertEquals(listOf(CachedVaultFile.Upload.NONE, CachedVaultFile.Upload.PENDING, CachedVaultFile.Upload.FAILED), files.map { it.upload })
		assertEquals(listOf(thumbnail, null, null), files.map { it.thumbnail })
		assertEquals(Date(4_000), files[0].modified)
		assertInstanceOf(CachedVaultFolder::class.java, listing.nodes[3])
		assertEquals(true, listing.isLoading)
		assertSame(authError, listing.error)
	}

	@Test
	fun `a listed node is found by its path again, also in the root folder`() {
		whenever(metadataCache.children(path(""))).thenReturn(DocumentMetadataCache.Children(listOf(entity("x.txt"), entity("b", mimeType = DocumentsContract.Document.MIME_TYPE_DIR)), isLoading = false, error = null))
		whenever(metadataCache.children(path("b"))).thenReturn(DocumentMetadataCache.Children(listOf(entity("b/y.txt")), isLoading = false, error = null))

		val root = inTest.list(CachedVaultFolder(cryptoCloud, ""), forceRefresh = false).nodes
		val nested = inTest.list(root[1] as CloudFolder, forceRefresh = false).nodes

		assertEquals(listOf("/x.txt", "/b"), root.map { it.path })
		assertEquals(listOf(path("x.txt"), path("b")), root.map(inTest::pathOf))
		assertEquals(listOf(path("b/y.txt")), nested.map(inTest::pathOf))
	}

	@Test
	fun `a refresh lists the folder from the cloud right away and reports its failure`() {
		whenever(metadataCache.children(path("a"))).thenReturn(DocumentMetadataCache.Children(emptyList(), isLoading = false, error = null))
		whenever(metadataCache.listNow(path("b"))).thenAnswer { throw FileNotFoundException("b is gone") }

		inTest.list(CachedVaultFolder(cryptoCloud, "/a"), forceRefresh = true)

		verify(metadataCache).listNow(path("a"))
		verify(metadataCache, never()).ensureFolder(any())
		assertThrows<NoSuchCloudFileException> { inTest.list(CachedVaultFolder(cryptoCloud, "/b"), forceRefresh = true) }
	}

	@Test
	fun `a change's node is found in the cache by its documentId, also after it was renamed or moved`() {
		whenever(metadataCache.create(any(), any(), any(), any(), any())).thenReturn(DocumentMetadataCache.Creation("1:7", CompletableFuture()))
		whenever(metadataCache.path("1:7")).thenReturn(path("c/renamed"))
		val renamed = entity("c/renamed", mimeType = DocumentsContract.Document.MIME_TYPE_DIR)
		whenever(metadataCache.cachedDocument(path("c/renamed"))).thenReturn(renamed)
		whenever(metadataCache.documentId(renamed)).thenReturn("1:7")
		whenever(cloudRepository.decryptedViewOf(vault)).thenReturn(cryptoCloud)

		val node = inTest.createFolder(path("a"), "new", userInitiated = true).cachedNode()

		assertEquals("/c/renamed", node?.path)
		assertEquals(path("c/renamed"), node?.let(inTest::pathOf))
	}

	@Test
	fun `a change's node isn't mistaken for another node that took its path meanwhile`() {
		whenever(metadataCache.create(any(), any(), any(), any(), any())).thenReturn(DocumentMetadataCache.Creation("1:7", CompletableFuture()))
		whenever(metadataCache.path("1:7")).thenReturn(path("a/new"))
		val other = entity("a/new", mimeType = DocumentsContract.Document.MIME_TYPE_DIR)
		whenever(metadataCache.cachedDocument(path("a/new"))).thenReturn(other)
		whenever(metadataCache.documentId(other)).thenReturn("1:8")

		assertNull(inTest.createFolder(path("a"), "new", userInitiated = true).cachedNode())
	}

	@Test
	fun `a change of a node that is gone meanwhile has no cached node, nor has a deletion`() {
		whenever(metadataCache.create(any(), any(), any(), any(), any())).thenReturn(DocumentMetadataCache.Creation("1:7", CompletableFuture()))
		whenever(metadataCache.path("1:7")).thenAnswer { throw FileNotFoundException("Unknown document: 1:7") }
		whenever(contentCache.delete(any(), any())).thenReturn(DocumentMetadataCache.Deletion(emptyList(), CompletableFuture()))

		assertNull(inTest.createFolder(path("a"), "new", userInitiated = true).cachedNode())
		assertNull(inTest.delete(path("a/x.txt"), userInitiated = true).cachedNode())
	}

	@Test
	fun `clearing the cache evicts the copies and thumbnails, whose size it reports`() {
		whenever(contentCache.size()).thenReturn(3_000L)
		whenever(thumbnailStore.size()).thenReturn(400L)

		inTest.clearCache()

		verify(contentCache).evictAll()
		assertEquals(3_400L, inTest.cacheSize())
	}

	@Test
	fun `uploads are resumed and thumbnails asked for by path`() {
		val file = CachedVaultFile(CachedVaultFolder(cryptoCloud, "/a"), "x.jpg", null, null, CachedVaultFile.Upload.NONE, null)

		inTest.resumeUploads(vault, listOf(path("a/x.jpg")), userInitiated = true)
		inTest.ensureThumbnails(listOf(file))

		verify(contentCache).resumeUploads(vault, listOf(path("a/x.jpg")), true)
		verify(thumbnailGenerator).request(listOf(path("a/x.jpg")))
	}

	private fun givenImport(result: CompletableFuture<VaultDocumentChangeResult<CloudFile>>): org.mockito.kotlin.KArgumentCaptor<(OutputStream) -> Unit> {
		val upload: DocumentMetadataCache.Upload = mock { on { this.result } doReturn result }
		val write = argumentCaptor<(OutputStream) -> Unit>()
		whenever(contentCache.importFile(any(), any(), any(), any(), any(), any<ProgressAware<UploadState>>(), anyOrNull(), write.capture())).thenReturn(upload)
		return write
	}

	private fun dataSource(open: () -> InputStream): DataSource {
		val dataSource: DataSource = mock { on { modifiedDate(any()) } doReturn Optional.of(Date(4_000)) }
		whenever(dataSource.open(any())).thenAnswer { open() }
		return dataSource
	}

	private fun path(path: String): VaultPath {
		return VaultPath(vault, path)
	}

	private fun entity(path: String, mimeType: String = "text/plain", status: String = DocumentMetadataEntity.STATUS_SYNCED, lastModified: Long? = null): DocumentMetadataEntity {
		return DocumentMetadataEntity().apply {
			id = path.hashCode().toLong()
			vaultId = vault.id
			this.path = path
			name = path.substringAfterLast('/')
			this.mimeType = mimeType
			this.status = status
			this.lastModified = lastModified
		}
	}
}
