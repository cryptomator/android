package org.cryptomator.presentation.docprovider

import android.provider.DocumentsContract.Document
import org.cryptomator.data.cloud.crypto.CryptoCloud
import org.cryptomator.data.cloud.crypto.CryptoFile
import org.cryptomator.data.cloud.crypto.RootCryptoFolder
import org.cryptomator.data.db.dao.DocumentMetadataDao
import org.cryptomator.data.db.entities.DocumentMetadataEntity
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.CloudFolder
import org.cryptomator.domain.CloudNode
import org.cryptomator.domain.CloudType
import org.cryptomator.domain.Vault
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.exception.CloudNodeAlreadyExistsException
import org.cryptomator.domain.exception.NetworkConnectionException
import org.cryptomator.domain.repository.CloudContentRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.io.FileNotFoundException
import java.util.Date
import java.util.concurrent.Executor

class DocumentMetadataCacheTest {

	private val vault = Vault.aVault().withId(VAULT_ID).withName("vault").withPath("/vault").withCloudType(CloudType.LOCAL).withPosition(0).withUnlocked(true).build()
	private val cryptoCloud: CryptoCloud = mock { on { type() } doReturn CloudType.CRYPTO }
	private val root = RootCryptoFolder(cryptoCloud)
	private val repository: CloudContentRepository<Cloud, CloudNode, CloudFolder, CloudFile> = mock()
	private val dao: DocumentMetadataDao = mock()
	private val listings = QueueExecutor()
	private val changedFolders = mutableListOf<VaultPath>()
	private val uploads = mutableListOf<Triple<VaultPath, VaultPath, Long?>>()
	private val localFile = File.createTempFile("local", ".txt")
	private var now = 1_000_000L
	private val direct = Executor { it.run() }
	private lateinit var helpers: HelpersMock
	private lateinit var inTest: DocumentMetadataCache

	@BeforeEach
	fun setup() {
		helpers = HelpersMock(repository, cryptoCloud, root, listOf(vault))
		inTest = DocumentMetadataCache(dao, CloudTaskScheduler(listings, direct, direct), { changedFolders.add(it) }) { now }
	}

	@AfterEach
	fun tearDown() {
		helpers.close()
		localFile.delete()
	}

	@Test
	fun `an upload overwrites the cloud file if it has not changed since the local copy was made`() {
		givenUpload(remoteModified = 5_400)
		val target = writeTarget("x.txt")

		upload(base = 5_000)

		verify(repository).write(eq(target), any(), any(), eq(true), any())
		verify(dao).markUploaded(ID, 3, 9_000, GENERATION)
		assertEquals(listOf(Triple(path("a/x.txt"), path("a/x.txt"), 9_000L)), uploads)
	}

	@Test
	fun `an upload keeps a cloud file that changed since the local copy was made and uploads a conflict copy`() {
		givenUpload(remoteModified = 7_000, remoteSize = 11)
		whenever(repository.file(eq(root), argThat { matches(CONFLICT_NAME) }, any())).thenReturn(mock())

		upload(base = 5_000)

		verify(repository, never()).write(any(), any(), any(), eq(true), any())
		verify(repository).write(any(), any(), any(), eq(false), any())
		verify(dao).store(argThat { path.matches(Regex("a/$CONFLICT_NAME")) && parentPath == "a" })
		verify(dao).markUploaded(ID, 11, 7_000, GENERATION)
		assertEquals(path("a/x.txt"), uploads.single().first)
		assertTrue(uploads.single().second.path.matches(Regex("a/$CONFLICT_NAME")))
	}

	@Test
	fun `an upload never overwrites a cloud file the local copy is not based on`() {
		givenUpload(remoteModified = 5_400, lastModified = null)
		val target = writeTarget("x.txt")

		upload(base = null)

		verify(repository).write(eq(target), any(), any(), eq(false), any())
	}

	@Test
	fun `an upload creates the file if it is not in the cloud`() {
		givenUpload(remoteModified = null)
		val target = writeTarget("x.txt")

		upload(base = 5_000)

		verify(repository).write(eq(target), any(), any(), eq(false), any())
		verify(dao).markUploaded(ID, 3, 9_000, GENERATION)
	}

	@Test
	fun `an upload whose name is taken in the cloud renames the file to a conflict name`() {
		givenUpload(remoteModified = null)
		val taken = writeTarget("x.txt")
		whenever(repository.write(eq(taken), any(), any(), any(), any())).thenThrow(CloudNodeAlreadyExistsException("x.txt"))
		whenever(repository.file(eq(root), argThat { matches(CONFLICT_NAME) }, any())).thenReturn(mock())

		upload(base = 5_000)

		verify(dao).renamePending(eq(VAULT_ID), eq("a/x.txt"), argThat { matches(Regex("a/$CONFLICT_NAME")) }, argThat { matches(CONFLICT_NAME) }, any())
		verify(repository, times(2)).write(any(), any(), any(), eq(false), any())
	}

	@Test
	fun `an upload is skipped if the file has been written again since`() {
		givenUpload(remoteModified = 5_400)
		whenever(dao.loadById(ID)).thenReturn(uploading().apply { uploadGeneration = GENERATION + 1 })

		upload(base = 5_000)

		verify(repository, never()).metadata(any())
		verify(repository, never()).write(any(), any(), any(), any(), any())
	}

	@Test
	fun `an upload is skipped if the file is being deleted`() {
		givenUpload(remoteModified = 5_400)
		whenever(dao.loadById(ID)).thenReturn(uploading().apply { status = DocumentMetadataEntity.STATUS_DELETING })

		upload(base = 5_000)

		verify(repository, never()).write(any(), any(), any(), any(), any())
	}

	@Test
	fun `a failed upload stays pending`() {
		givenUpload(remoteModified = 5_400)
		whenever(repository.write(any(), any(), any(), any(), any())).thenThrow(NetworkConnectionException())
		writeTarget("x.txt")

		upload(base = 5_000)

		verify(dao, never()).markUploaded(any(), anyOrNull(), anyOrNull(), any())
		verify(dao, never()).discardPendingChange(any())
	}

	@Test
	fun `creating a folder whose name is taken in the cloud renames it to a conflict name`() {
		whenever(dao.insert(any())).thenReturn(ID)
		whenever(dao.loadById(ID)).thenReturn(entity("new", Document.MIME_TYPE_DIR).apply { status = DocumentMetadataEntity.STATUS_CREATING })
		val taken: CloudFolder = mock()
		whenever(repository.folder(root, "new")).thenReturn(taken)
		whenever(repository.create(taken)).thenThrow(CloudNodeAlreadyExistsException("new"))
		whenever(repository.folder(eq(root), argThat { matches(CONFLICT_FOLDER_NAME) })).thenReturn(mock())

		inTest.create(VaultPath(vault), "new", isFolder = true)
		listings.runAll()

		verify(dao).renamePending(eq(VAULT_ID), eq("new"), argThat { matches(CONFLICT_FOLDER_NAME) }, argThat { matches(CONFLICT_FOLDER_NAME) }, eq(Document.MIME_TYPE_DIR))
		verify(dao).markCreated(ID, null, null)
		verify(dao, never()).discardPendingChange(any())
	}

	@Test
	fun `a change the cloud rejects is discarded`() {
		val pending = entity("new", Document.MIME_TYPE_DIR).apply { status = DocumentMetadataEntity.STATUS_CREATING }
		whenever(dao.insert(any())).thenReturn(ID)
		whenever(dao.loadById(ID)).thenReturn(pending)
		val folder: CloudFolder = mock()
		whenever(repository.folder(root, "new")).thenReturn(folder)
		whenever(repository.create(folder)).thenThrow(NetworkConnectionException())

		inTest.create(VaultPath(vault), "new", isFolder = true)
		changedFolders.clear()
		listings.runAll()

		verify(dao).discardPendingChange(pending)
		assertEquals(listOf(VaultPath(vault)), changedFolders)
	}

	@Test
	fun `a folder created through the provider is known to be empty`() {
		whenever(dao.insert(any())).thenReturn(ID)

		inTest.create(VaultPath(vault), "new", isFolder = true)

		verify(dao).insert(argThat { path == "new" && lastEnumeratedAt == now && status == DocumentMetadataEntity.STATUS_CREATING })
	}

	@Test
	fun `a moved node records where it is in the cloud`() {
		whenever(dao.load(VAULT_ID, "a.txt")).thenReturn(entity("a.txt"))
		val source: CloudFile = mock()
		val target: CloudFile = mock()
		whenever(repository.file(root, "a.txt")).thenReturn(source)
		whenever(repository.file(root, "b.txt")).thenReturn(target)

		inTest.move(path("a.txt"), VaultPath(vault), "b.txt")
		listings.runAll()

		verify(dao).move(VAULT_ID, "a.txt", "b.txt", "", "b.txt", "application/octet-stream")
		verify(repository).move(source, target)
		verify(dao).markMoved(ID, "b.txt")
	}

	@Test
	fun `a folder that has never been listed is listed in the background`() {
		whenever(dao.load(VAULT_ID, "a")).thenReturn(entity("a", Document.MIME_TYPE_DIR))
		val listed = listOf(cryptoFile("a/x.txt"))
		whenever(repository.list(root)).thenReturn(listed)

		val children = inTest.children(path("a"))

		assertTrue(children.isLoading)
		verify(repository, never()).list(any())
		listings.runAll()
		verify(dao).storeChildren(argThat { path == "a" && lastEnumeratedAt == now }, argThat { single().path == "a/x.txt" })
		assertEquals(listOf(path("a")), changedFolders)
	}

	@Test
	fun `a recently listed folder is answered from the cache`() {
		whenever(dao.load(VAULT_ID, "a")).thenReturn(entity("a", Document.MIME_TYPE_DIR).apply { lastEnumeratedAt = now - 1_000 })

		val children = inTest.children(path("a"))

		assertFalse(children.isLoading)
		assertTrue(listings.isEmpty())
	}

	@Test
	fun `a failed first listing is reported and not retried right away`() {
		whenever(dao.load(VAULT_ID, "a")).thenReturn(entity("a", Document.MIME_TYPE_DIR))
		whenever(repository.list(root)).thenThrow(NetworkConnectionException())
		inTest.children(path("a"))
		listings.runAll()

		val children = inTest.children(path("a"))

		assertFalse(children.isLoading)
		assertInstanceOf(NetworkConnectionException::class.java, children.error)
		assertTrue(listings.isEmpty())
		now += 30_000
		assertTrue(inTest.children(path("a")).isLoading)
	}

	@Test
	fun `refreshing lists a folder again right away, even after a failed listing`() {
		whenever(dao.load(VAULT_ID, "a")).thenReturn(entity("a", Document.MIME_TYPE_DIR).apply { lastEnumeratedAt = now })
		whenever(repository.list(root)).thenThrow(NetworkConnectionException())
		inTest.refresh(path("a"))
		listings.runAll()

		inTest.refresh(path("a"))

		assertFalse(listings.isEmpty())
	}

	@Test
	fun `an error is only reported for a folder that has never been listed`() {
		whenever(dao.load(VAULT_ID, "a")).thenReturn(entity("a", Document.MIME_TYPE_DIR).apply { lastEnumeratedAt = now - 60_000 })
		whenever(repository.list(root)).thenThrow(NetworkConnectionException())
		inTest.children(path("a"))
		listings.runAll()

		assertNull(inTest.children(path("a")).error)
	}

	@Test
	fun `a listing during which an upload finished is repeated`() {
		givenUpload(remoteModified = null)
		writeTarget("x.txt")
		whenever(dao.load(VAULT_ID, "a")).thenReturn(entity("a", Document.MIME_TYPE_DIR))
		var listCalls = 0
		whenever(repository.list(root)).thenAnswer {
			listCalls++
			if (listCalls == 1) {
				upload(base = 5_000)
				listOf(cryptoFile("a/before-upload.txt"))
			} else {
				listOf(cryptoFile("a/after-upload.txt"))
			}
		}

		inTest.children(path("a"))
		listings.runAll()

		assertEquals(2, listCalls)
		verify(dao).storeChildren(any(), argThat { single().path == "a/after-upload.txt" })
		verify(dao, never()).storeChildren(any(), argThat { single().path == "a/before-upload.txt" })
	}

	@Test
	fun `creating a file never overwrites a file in the cloud`() {
		whenever(dao.insert(any())).thenReturn(ID)
		val target = writeTarget("new.txt", root)
		val createdFile = cryptoFile("new.txt", size = 0, modified = 4_000)
		whenever(repository.write(eq(target), any(), any(), any(), any())).thenReturn(createdFile)

		val documentId = inTest.create(VaultPath(vault), "new.txt", isFolder = false)
		listings.runAll()

		assertEquals("$VAULT_ID:$ID", documentId)
		verify(repository).write(eq(target), any(), any(), eq(false), eq(0L))
		verify(dao).markCreated(ID, 0L, 4_000L)
	}

	@Test
	fun `creating a file whose name is taken in the cloud renames it to a conflict name`() {
		whenever(dao.insert(any())).thenReturn(ID)
		whenever(dao.loadById(ID)).thenReturn(entity("new.txt").apply { status = DocumentMetadataEntity.STATUS_CREATING })
		val taken = writeTarget("new.txt", root)
		whenever(repository.write(eq(taken), any(), any(), any(), any())).thenThrow(CloudNodeAlreadyExistsException("new.txt"))
		val renamed: CloudFile = mock()
		whenever(repository.file(eq(root), argThat { matches(CONFLICT_FILE_NAME) }, any())).thenReturn(renamed)
		val createdFile = cryptoFile("new.txt")
		whenever(repository.write(eq(renamed), any(), any(), any(), any())).thenReturn(createdFile)

		inTest.create(VaultPath(vault), "new.txt", isFolder = false)
		listings.runAll()

		verify(dao).renamePending(eq(VAULT_ID), eq("new.txt"), argThat { matches(CONFLICT_FILE_NAME) }, argThat { matches(CONFLICT_FILE_NAME) }, any())
		verify(repository).write(eq(renamed), any(), any(), eq(false), any())
	}

	@Test
	fun `a node is moved to an unused name if the target name is taken in the cache`() {
		whenever(dao.load(VAULT_ID, "a.txt")).thenReturn(entity("a.txt"))
		whenever(dao.load(VAULT_ID, "b.txt")).thenReturn(entity("b.txt").apply { id = ID + 1 })
		val source: CloudFile = mock()
		val target: CloudFile = mock()
		whenever(repository.file(root, "a.txt")).thenReturn(source)
		whenever(repository.file(root, "b.txt_")).thenReturn(target)

		inTest.move(path("a.txt"), VaultPath(vault), "b.txt")
		listings.runAll()

		verify(dao).move(VAULT_ID, "a.txt", "b.txt_", "", "b.txt_", "application/octet-stream")
		verify(repository).move(source, target)
	}

	@Test
	fun `a folder is moved as a folder`() {
		whenever(dao.load(VAULT_ID, "a")).thenReturn(entity("a", Document.MIME_TYPE_DIR))
		val source: CloudFolder = mock()
		val target: CloudFolder = mock()
		whenever(repository.folder(root, "a")).thenReturn(source)
		whenever(repository.folder(root, "b")).thenReturn(target)

		inTest.move(path("a"), VaultPath(vault), "b")
		listings.runAll()

		verify(repository).move(source, target)
		verify(dao).markMoved(ID, "b")
	}

	@Test
	fun `a move the cloud rejects is undone`() {
		whenever(dao.load(VAULT_ID, "a.txt")).thenReturn(entity("a.txt"), null)
		val moved = entity("b.txt").apply { sourcePath = "a.txt" }
		whenever(dao.loadById(ID)).thenReturn(moved)
		whenever(repository.move(anyOrNull<CloudFile>(), anyOrNull<CloudFile>())).thenThrow(NetworkConnectionException())

		inTest.move(path("a.txt"), VaultPath(vault), "b.txt")
		listings.runAll()

		verify(dao).revertMove(ID, "application/octet-stream")
		verify(dao, never()).discardPendingChange(any())
	}

	@Test
	fun `deleting a folder deletes it in the cloud and returns its descendants for revoking their grants`() {
		whenever(dao.load(VAULT_ID, "a")).thenReturn(entity("a", Document.MIME_TYPE_DIR))
		whenever(dao.loadById(ID)).thenReturn(entity("a", Document.MIME_TYPE_DIR).apply { status = DocumentMetadataEntity.STATUS_DELETING })
		whenever(dao.loadDescendantIds(VAULT_ID, "a")).thenReturn(listOf(8L, 9L))
		val folder: CloudFolder = mock()
		whenever(repository.folder(root, "a")).thenReturn(folder)

		val descendantIds = inTest.delete(path("a"))
		listings.runAll()

		assertEquals(listOf("$VAULT_ID:8", "$VAULT_ID:9"), descendantIds)
		inOrder(dao) {
			verify(dao).loadDescendantIds(VAULT_ID, "a")
			verify(dao).markDeletingWithDescendants(VAULT_ID, "a")
		}
		verify(repository).delete(folder)
		verify(dao).deleteSubtree(VAULT_ID, "a")
	}

	@Test
	fun `a cached document is used if its folder can't be listed`() {
		val cached = entity("a/x.txt")
		whenever(dao.load(VAULT_ID, "a")).thenReturn(entity("a", Document.MIME_TYPE_DIR).apply { lastEnumeratedAt = now - 60_000 })
		whenever(dao.load(VAULT_ID, "a/x.txt")).thenReturn(cached)
		whenever(repository.list(root)).thenThrow(NetworkConnectionException())
		val cache = DocumentMetadataCache(dao, CloudTaskScheduler(direct, direct, direct), { changedFolders.add(it) }) { now }

		assertEquals(cached, cache.currentDocument(path("a/x.txt")))
	}

	@Test
	fun `a listing during which a creation finished is repeated`() {
		whenever(dao.insert(any())).thenReturn(ID)
		whenever(repository.folder(root, "new")).thenReturn(mock())
		whenever(dao.load(VAULT_ID, "b")).thenReturn(entity("b", Document.MIME_TYPE_DIR))
		var listCalls = 0
		whenever(repository.list(root)).thenAnswer {
			listCalls++
			if (listCalls == 1) {
				inTest.create(path("b"), "new", isFolder = true)
				listings.runAll()
				listOf(cryptoFile("b/before-creation.txt"))
			} else {
				listOf(cryptoFile("b/after-creation.txt"))
			}
		}

		inTest.children(path("b"))
		listings.runAll()

		verify(dao).markCreated(ID, null, null)
		assertEquals(2, listCalls)
		verify(dao).storeChildren(any(), argThat { single().path == "b/after-creation.txt" })
	}

	@Test
	fun `a change in another folder does not repeat a listing`() {
		whenever(dao.insert(any())).thenReturn(ID)
		whenever(repository.folder(root, "new")).thenReturn(mock())
		whenever(dao.load(VAULT_ID, "b")).thenReturn(entity("b", Document.MIME_TYPE_DIR))
		var listCalls = 0
		whenever(repository.list(root)).thenAnswer {
			listCalls++
			inTest.create(VaultPath(vault), "new", isFolder = true)
			listings.runAll()
			listOf(cryptoFile("b/listed.txt"))
		}

		inTest.children(path("b"))
		listings.runAll()

		verify(dao).markCreated(ID, null, null)
		assertEquals(1, listCalls)
		verify(dao).storeChildren(any(), argThat { single().path == "b/listed.txt" })
	}

	@Test
	fun `a listing that keeps being overtaken by changes to the folder gives up`() {
		whenever(dao.insert(any())).thenReturn(ID)
		whenever(repository.folder(root, "new")).thenReturn(mock())
		whenever(dao.load(VAULT_ID, "b")).thenReturn(entity("b", Document.MIME_TYPE_DIR))
		var listCalls = 0
		whenever(repository.list(root)).thenAnswer {
			listCalls++
			inTest.create(path("b"), "new", isFolder = true)
			listings.runAll()
			listOf(cryptoFile("b/listed.txt"))
		}

		inTest.children(path("b"))
		listings.runAll()

		assertEquals(3, listCalls)
		verify(dao, never()).storeChildren(any(), any())
		assertInstanceOf(BackendException::class.java, inTest.children(path("b")).error)
		assertTrue(listings.isEmpty())
	}

	@Test
	fun `a new file's upload is based on the version its creation left in the cloud`() {
		givenUpload(remoteModified = 5_400, lastModified = 5_000)
		val target = writeTarget("x.txt")

		upload(base = null)

		verify(repository).write(eq(target), any(), any(), eq(true), any())
	}

	@Test
	fun `a file is moved from its folder into another one`() {
		val folderA: CloudFolder = mock()
		val folderB: CloudFolder = mock()
		helpers.close()
		helpers = HelpersMock(repository, cryptoCloud, root, listOf(vault)) { if (it.path == "a") folderA else if (it.path == "b") folderB else root }
		whenever(dao.load(VAULT_ID, "a/x.txt")).thenReturn(entity("a/x.txt"))
		whenever(dao.load(VAULT_ID, "b")).thenReturn(entity("b", Document.MIME_TYPE_DIR).apply { id = ID + 1 })
		val source: CloudFile = mock()
		val target: CloudFile = mock()
		whenever(repository.file(folderA, "x.txt")).thenReturn(source)
		whenever(repository.file(folderB, "x.txt")).thenReturn(target)

		inTest.move(path("a/x.txt"), path("b"), "x.txt")
		listings.runAll()

		verify(repository).move(source, target)
		verify(dao).markMoved(ID, "b/x.txt")
	}

	@Test
	fun `changes interrupted by the end of the process are dropped when the cache is created`() {
		val interrupted = entity("new").apply { status = DocumentMetadataEntity.STATUS_CREATING }
		whenever(dao.loadInterruptedChanges()).thenReturn(listOf(interrupted))

		DocumentMetadataCache(dao, CloudTaskScheduler(direct, direct, direct), { changedFolders.add(it) }) { now }

		verify(dao).discardPendingChange(interrupted)
	}

	@Test
	fun `deleting a file deletes it in the cloud`() {
		whenever(dao.load(VAULT_ID, "x.txt")).thenReturn(entity("x.txt"))
		whenever(dao.loadById(ID)).thenReturn(entity("x.txt").apply { status = DocumentMetadataEntity.STATUS_DELETING })
		val file: CloudFile = mock()
		whenever(repository.file(root, "x.txt")).thenReturn(file)

		inTest.delete(path("x.txt"))
		listings.runAll()

		verify(repository).delete(file)
		verify(dao).deleteSubtree(VAULT_ID, "x.txt")
	}

	@Test
	fun `abandoning an upload ends it and lists its folder again`() {
		whenever(dao.load(VAULT_ID, "a/x.txt")).thenReturn(uploading())

		inTest.abandonUpload(path("a/x.txt"))

		verify(dao).markSynced(ID)
		verify(dao).updateLastEnumeratedAt(VAULT_ID, "a", null)
	}

	@Test
	fun `documentIds resolve to the paths of their vault`() {
		val otherVaultsNode = entity("b.txt").apply {
			id = ID + 1
			vaultId = VAULT_ID + 1
		}
		val deleting = entity("c.txt").apply {
			id = ID + 2
			status = DocumentMetadataEntity.STATUS_DELETING
		}
		whenever(dao.loadById(ID)).thenReturn(entity("a/x.txt"))
		whenever(dao.loadById(ID + 1)).thenReturn(otherVaultsNode)
		whenever(dao.loadById(ID + 2)).thenReturn(deleting)

		assertEquals(VaultPath(vault), inTest.path("$VAULT_ID"))
		assertEquals(path("a/x.txt"), inTest.path("$VAULT_ID:$ID"))
		assertThrows<FileNotFoundException> { inTest.path("$VAULT_ID:${ID + 1}") }
		assertThrows<FileNotFoundException> { inTest.path("$VAULT_ID:${ID + 2}") }
		assertThrows<FileNotFoundException> { inTest.path("$VAULT_ID:${ID + 3}") }
		assertThrows<FileNotFoundException> { inTest.path("${VAULT_ID + 5}:$ID") }
		assertThrows<IllegalArgumentException> { inTest.path("x:$ID") }
	}

	private fun givenUpload(remoteModified: Long?, remoteSize: Long = 7, lastModified: Long? = 5_000) {
		var generation = GENERATION - 1
		val snapshot = {
			uploading().apply {
				this.lastModified = lastModified
				uploadGeneration = generation
			}
		}
		whenever(dao.load(VAULT_ID, "a/x.txt")).thenAnswer { snapshot() }
		whenever(dao.loadById(ID)).thenAnswer { snapshot() }
		doAnswer { generation += 1 }.whenever(dao).markUploading(VAULT_ID, "a/x.txt")
		val cloudFile: CloudFile = mock()
		whenever(repository.file(root, "x.txt")).thenReturn(cloudFile)
		if (remoteModified != null) {
			val remoteFile: CloudFile = mock {
				on { modified } doReturn Date(remoteModified)
				on { size } doReturn remoteSize
			}
			whenever(repository.metadata(cloudFile)).thenReturn(remoteFile)
		}
		val uploadedFile = cryptoFile("a/uploaded.txt", size = 3, modified = 9_000)
		whenever(repository.write(any(), any(), any(), any(), any())).thenReturn(uploadedFile)
	}

	private fun writeTarget(name: String, folder: CloudFolder = root): CloudFile {
		val target: CloudFile = mock()
		whenever(repository.file(eq(folder), eq(name), any())).thenReturn(target)
		return target
	}

	private fun upload(base: Long?) {
		inTest.upload(path("a/x.txt"), localFile, { base }) { path, uploadedPath, lastModified -> uploads.add(Triple(path, uploadedPath, lastModified)) }
	}

	private fun uploading(): DocumentMetadataEntity {
		return entity("a/x.txt").apply {
			status = DocumentMetadataEntity.STATUS_UPLOADING
			uploadGeneration = GENERATION
		}
	}

	private fun entity(path: String, mimeType: String = "text/plain"): DocumentMetadataEntity {
		return DocumentMetadataEntity().apply {
			id = ID
			vaultId = VAULT_ID
			this.path = path
			parentPath = if (path.contains('/')) path.substringBeforeLast('/') else ""
			name = path.substringAfterLast('/')
			this.mimeType = mimeType
		}
	}

	private fun cryptoFile(path: String, size: Long = 1, modified: Long = 1_000): CryptoFile {
		val cloudFile: CloudFile = mock { on { this.modified } doReturn Date(modified) }
		return CryptoFile(root, path.substringAfterLast('/'), "/$path", size, cloudFile)
	}

	private fun path(path: String): VaultPath {
		return VaultPath(vault, path)
	}

	private class QueueExecutor : Executor {

		private val tasks = ArrayDeque<Runnable>()

		override fun execute(command: Runnable) {
			tasks.addLast(command)
		}

		fun isEmpty(): Boolean {
			return tasks.isEmpty()
		}

		fun runAll() {
			while (tasks.isNotEmpty()) {
				tasks.removeFirst().run()
			}
		}
	}

	companion object {

		private const val VAULT_ID = 1L
		private const val ID = 7L
		private const val GENERATION = 3L
		private val CONFLICT_NAME = Regex("x \\([0-9A-F]{5}\\)\\.txt")
		private val CONFLICT_FOLDER_NAME = Regex("new \\([0-9A-F]{5}\\)")
		private val CONFLICT_FILE_NAME = Regex("new \\([0-9A-F]{5}\\)\\.txt")
	}
}
