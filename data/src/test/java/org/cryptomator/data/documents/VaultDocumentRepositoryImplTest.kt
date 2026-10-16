package org.cryptomator.data.documents

import android.content.Context
import org.cryptomator.data.cloud.crypto.CryptoCloud
import org.cryptomator.data.cloud.crypto.CryptoFile
import org.cryptomator.data.cloud.crypto.RootCryptoFolder
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
import org.cryptomator.domain.repository.VaultDocumentChangeResult
import org.cryptomator.domain.usecases.ProgressAware
import org.cryptomator.domain.usecases.cloud.DataSource
import org.cryptomator.domain.usecases.cloud.UploadState
import org.cryptomator.util.Optional
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
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
	private val inTest = VaultDocumentRepositoryImpl(context, { cachesCreated++; metadataCache }, { cachesCreated++; contentCache })

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
		whenever(metadataCache.create(any(), any(), any(), any())).thenReturn(DocumentMetadataCache.Creation("1:7", CompletableFuture.completedFuture(VaultDocumentChangeResult<CloudNode>(path("a/new (1A2B3)"), created))))

		val result = inTest.createFolder(path("a"), "new").await()

		verify(metadataCache).create(path("a"), "new", true, true)
		assertEquals(path("a/new (1A2B3)"), result.path)
		assertSame(created, result.node)
	}

	@Test
	fun `a node that vanished from the cache meanwhile counts as gone from the cloud`() {
		whenever(metadataCache.move(any(), any(), any(), any())).thenAnswer { throw FileNotFoundException("Unknown document: 1:7") }

		assertThrows<NoSuchCloudFileException> { inTest.move(path("a/x.txt"), path("b")) }
	}

	@Test
	fun `a node the cache has never listed is looked up before it is renamed, moved or deleted`() {
		whenever(metadataCache.move(any(), any(), any(), any())).thenReturn(CompletableFuture())
		whenever(contentCache.delete(any())).thenReturn(DocumentMetadataCache.Deletion(emptyList(), CompletableFuture()))

		inTest.rename(path("a/x.txt"), "y.txt")
		inTest.move(path("a/x.txt"), path("b"))
		inTest.delete(path("a/x.txt"))

		inOrder(metadataCache, contentCache) {
			verify(metadataCache).requireKnownDocument(path("a/x.txt"))
			verify(metadataCache).move(path("a/x.txt"), path("a"), "y.txt", true)
			verify(metadataCache).requireKnownDocument(path("a/x.txt"))
			verify(metadataCache).move(path("a/x.txt"), path("b"), "x.txt", true)
			verify(metadataCache).requireKnownDocument(path("a/x.txt"))
			verify(contentCache).delete(path("a/x.txt"))
		}
	}

	@Test
	fun `a node the cloud no longer has is not changed`() {
		whenever(metadataCache.requireKnownDocument(path("a/x.txt"))).thenAnswer { throw NoSuchCloudFileException("1:7") }

		assertThrows<NoSuchCloudFileException> { inTest.delete(path("a/x.txt")) }
		verifyNoInteractions(contentCache)
	}

	@Test
	fun `an import keeps the source's modification date and reports where the file was uploaded to`() {
		val result = CompletableFuture<VaultDocumentChangeResult<CloudFile>>()
		val uploaded: CloudFile = mock()
		val write = givenImport(result)

		val vaultImport = inTest.importFile(path("a"), "x.txt", dataSource { "content".byteInputStream() }, replace = true, requireListing = true, progressAware = mock())
		val copy = java.io.ByteArrayOutputStream()
		write.firstValue.invoke(copy)
		result.complete(VaultDocumentChangeResult(path("a/x (1A2B3).txt"), uploaded))

		verify(contentCache).importFile(eq(path("a")), eq("x.txt"), eq(true), eq(true), any(), eq(4_000L), any())
		assertEquals("content", copy.toString())
		assertEquals(path("a/x (1A2B3).txt"), vaultImport.await().path)
		assertSame(uploaded, vaultImport.await().node)
	}

	@Test
	fun `an import that was stopped ends like a canceled use case`() {
		val result = CompletableFuture<VaultDocumentChangeResult<CloudFile>>()
		givenImport(result)
		val vaultImport = inTest.importFile(path("a"), "x.txt", dataSource { "content".byteInputStream() }, replace = false, requireListing = false, progressAware = mock())

		vaultImport.cancel()
		result.completeExceptionally(java.util.concurrent.CancellationException("stopped"))

		verify(contentCache).cancel(any())
		assertThrows<CancellationException> { vaultImport.await() }
	}

	@Test
	fun `an import whose source can't be read fails like a failed upload outside vaults`() {
		val write = givenImport(CompletableFuture())
		inTest.importFile(path("a"), "x.txt", dataSource { throw IOException("source gone") }, replace = false, requireListing = false, progressAware = mock())

		val e = assertThrows<FatalBackendException> { write.firstValue.invoke(java.io.ByteArrayOutputStream()) }
		assertInstanceOf(IOException::class.java, e.cause)
	}

	@Test
	fun `a name another app chose is not taken for a path`() {
		assertThrows<IllegalFileNameException> {
			inTest.importFile(path("a"), "Work/payroll.pdf", dataSource { "content".byteInputStream() }, replace = false, requireListing = false, progressAware = mock())
		}
		verify(contentCache, never()).importFile(any(), any(), any(), any(), any(), anyOrNull(), any())
	}

	private fun givenImport(result: CompletableFuture<VaultDocumentChangeResult<CloudFile>>): org.mockito.kotlin.KArgumentCaptor<(OutputStream) -> Unit> {
		val upload: DocumentMetadataCache.Upload = mock { on { this.result } doReturn result }
		val write = argumentCaptor<(OutputStream) -> Unit>()
		whenever(contentCache.importFile(any(), any(), any(), any(), any<ProgressAware<UploadState>>(), anyOrNull(), write.capture())).thenReturn(upload)
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
}
