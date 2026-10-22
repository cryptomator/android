package org.cryptomator.data.documents

import android.provider.DocumentsContract.Document
import org.cryptomator.data.cloud.crypto.CryptoCloud
import org.cryptomator.data.cloud.crypto.CryptoFile
import org.cryptomator.data.cloud.crypto.CryptoFolder
import org.cryptomator.data.cloud.crypto.RootCryptoFolder
import org.cryptomator.data.db.dao.DocumentMetadataDao
import org.cryptomator.data.db.entities.DocumentMetadataEntity
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.CloudFolder
import org.cryptomator.domain.CloudNode
import org.cryptomator.domain.CloudType
import org.cryptomator.domain.Vault
import org.cryptomator.domain.VaultPath
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.exception.CloudNodeAlreadyExistsException
import org.cryptomator.domain.exception.CloudNodeIsFolderException
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.domain.exception.MissingCryptorException
import org.cryptomator.domain.exception.NetworkConnectionException
import org.cryptomator.domain.exception.NoSuchCloudFileException
import org.cryptomator.domain.exception.NotFoundException
import org.cryptomator.domain.exception.authentication.NoAuthenticationProvidedException
import org.cryptomator.domain.repository.CloudContentRepository
import org.cryptomator.domain.repository.CloudRepository
import org.cryptomator.domain.repository.VaultDocumentListener
import org.cryptomator.domain.repository.VaultRepository
import org.cryptomator.domain.usecases.ProgressAware
import org.cryptomator.domain.usecases.cloud.DataSource
import org.cryptomator.domain.usecases.cloud.DownloadState
import org.cryptomator.domain.usecases.cloud.Progress
import org.cryptomator.domain.usecases.cloud.UploadState
import org.cryptomator.util.file.MimeTypes
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.isNull
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import java.util.Date
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

class DocumentMetadataCacheTest {

	private val vault = Vault.aVault().withId(VAULT_ID).withName("vault").withPath("/vault").withCloudType(CloudType.LOCAL).withPosition(0).withUnlocked(true).build()
	private val cryptoCloud: CryptoCloud = mock { on { type() } doReturn CloudType.CRYPTO }
	private val root = RootCryptoFolder(cryptoCloud)
	private val repository: CloudContentRepository<Cloud, CloudNode, CloudFolder, CloudFile> = mock()
	private val cloudRepository: CloudRepository = mock { on { decryptedViewOf(any()) } doReturn cryptoCloud }
	private val vaultRepository: VaultRepository = mock { on { vaults() } doReturn listOf(vault) }
	private val dao: DocumentMetadataDao = mock()
	private val listings = QueueExecutor()
	private val changedFolders = mutableListOf<VaultPath>()
	private val events = VaultDocumentEvents().apply { addListener { changedFolders.add(it) } }
	private val uploads = mutableListOf<Triple<VaultPath, VaultPath, Long?>>()
	private val localFile = File.createTempFile("local", ".txt")
	private var now = 1_000_000L
	private val direct = Executor { it.run() }
	private lateinit var inTest: DocumentMetadataCache

	@BeforeEach
	fun setup() {
		whenever(repository.root(cryptoCloud)).thenReturn(root)
		whenever(repository.resolve(eq(cryptoCloud), any())).thenReturn(root)
		// a node is visible unless it is being deleted itself
		whenever(dao.loadVisible(any(), any())).thenAnswer { dao.load(it.getArgument(0), it.getArgument(1))?.takeIf { metadata -> metadata.pendingDelete == null } }
		whenever(dao.loadVisibleById(any())).thenAnswer { dao.loadById(it.getArgument(0))?.takeIf { metadata -> metadata.pendingDelete == null } }
		inTest = cache(CloudTaskScheduler(listings, direct, direct))
	}

	@AfterEach
	fun tearDown() {
		localFile.delete()
	}

	private fun cache(scheduler: CloudTaskScheduler): DocumentMetadataCache {
		return DocumentMetadataCache(dao, scheduler, events, repository, cloudRepository, vaultRepository, mock<MimeTypes>(), direct, firstWriteGracePeriod = 0) { now }
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
		localFile.writeText("local-content")
		givenUpload(remoteModified = 7_000, remoteSize = 11)
		whenever(repository.file(eq(root), argThat { matches(CONFLICT_NAME) }, any())).thenReturn(mock())

		upload(base = 5_000)

		verify(repository, never()).read(any(), any(), any())

		verify(repository, never()).write(any(), any(), any(), eq(true), any())
		verify(repository).write(any(), any(), any(), eq(false), any())
		verify(dao).store(argThat { path.matches(Regex("a/$CONFLICT_NAME")) && parentPath == "a" })
		verify(dao).markUploaded(ID, 11, 7_000, GENERATION)
		assertEquals(path("a/x.txt"), uploads.single().first)
		assertTrue(uploads.single().second.path.matches(Regex("a/$CONFLICT_NAME")))
	}

	@Test
	fun `an upload to a cloud file that changed to the local copy's content records it instead of uploading a conflict copy`() {
		localFile.writeText("local-content")
		givenUpload(remoteModified = 7_000, remoteSize = localFile.length())
		givenCloudContent("local-content")

		upload(base = 5_000)

		verify(repository, never()).write(any(), any(), any(), any(), any())
		verify(dao, never()).store(any())
		verify(dao).markUploaded(ID, localFile.length(), 7_000, GENERATION)
		assertEquals(listOf(Triple(path("a/x.txt"), path("a/x.txt"), 7_000L)), uploads)
	}

	@Test
	fun `an upload of a new file whose name the cloud holds with the same content records it instead of renaming it`() {
		localFile.writeText("local-content")
		givenUpload(remoteModified = 7_000, remoteSize = localFile.length(), lastModified = null)
		givenCloudContent("local-content")

		upload(base = null)

		verify(repository, never()).write(any(), any(), any(), any(), any())
		verify(dao, never()).renamePending(any(), any(), any(), any(), any(), anyOrNull())
		verify(dao).markUploaded(ID, localFile.length(), 7_000, GENERATION)
	}

	@Test
	fun `a stopped upload ends its comparison while the cloud file is downloaded`() {
		localFile.writeText("local-content")
		givenUpload(remoteModified = 7_000, remoteSize = localFile.length())
		whenever(repository.read(any(), any(), any())).thenAnswer {
			whenever(dao.loadById(ID)).thenReturn(uploading().apply { pendingDelete = 1 })
			inTest.delete(path("a/x.txt"))
			// the backend reports the download's progress before any decrypted content reaches the stream
			it.getArgument<ProgressAware<DownloadState>>(2).onProgress(Progress.started(DownloadState.download(it.getArgument(0))))
		}
		whenever(repository.file(eq(root), argThat { matches(CONFLICT_NAME) }, any())).thenReturn(mock())

		val upload = upload(base = 5_000)

		assertFailsWith<CancellationException>(upload.result)
		verify(repository, never()).write(any(), any(), any(), any(), any())
		verify(dao, never()).store(any())
	}

	@Test
	fun `a stopped upload ends its comparison while the cloud file's content is read`() {
		localFile.writeText("local-content")
		givenUpload(remoteModified = 7_000, remoteSize = localFile.length())
		whenever(repository.read(any(), any(), any())).thenAnswer {
			whenever(dao.loadById(ID)).thenReturn(uploading().apply { pendingDelete = 1 })
			inTest.delete(path("a/x.txt"))
			it.getArgument<OutputStream>(1).write("local-content".toByteArray())
		}

		val upload = upload(base = 5_000)

		assertFailsWith<CancellationException>(upload.result)
		verify(repository, never()).write(any(), any(), any(), any(), any())
		verify(dao, never()).markUploaded(any(), anyOrNull(), anyOrNull(), any())
	}

	@Test
	fun `an upload to a cloud file that changed to other content of the same size uploads a conflict copy`() {
		localFile.writeText("local-content")
		givenUpload(remoteModified = 7_000, remoteSize = localFile.length())
		givenCloudContent("other-content")
		whenever(repository.file(eq(root), argThat { matches(CONFLICT_NAME) }, any())).thenReturn(mock())

		upload(base = 5_000)

		verify(repository).write(any(), any(), any(), eq(false), any())
		verify(dao).store(argThat { path.matches(Regex("a/$CONFLICT_NAME")) })
	}

	@Test
	fun `an upload to a changed cloud file that can't be read for comparing uploads a conflict copy`() {
		localFile.writeText("local-content")
		givenUpload(remoteModified = 7_000, remoteSize = localFile.length())
		whenever(repository.read(any(), any(), any())).thenThrow(NetworkConnectionException())
		whenever(repository.file(eq(root), argThat { matches(CONFLICT_NAME) }, any())).thenReturn(mock())

		upload(base = 5_000)

		verify(dao).store(argThat { path.matches(Regex("a/$CONFLICT_NAME")) })
	}

	@Test
	fun `an upload's conflict copy is stored next to where the file is in the cache when the upload ends`() {
		givenUpload(remoteModified = 7_000, remoteSize = 11)
		// the folder a has been renamed to b in the cache since the upload was requested
		whenever(dao.loadById(ID)).thenReturn(uploading().apply {
			path = "b/x.txt"
			parentPath = "b"
		})
		whenever(repository.file(eq(root), argThat { matches(CONFLICT_NAME) }, any())).thenReturn(mock())

		upload(base = 5_000)

		verify(dao).store(argThat { path.matches(Regex("b/$CONFLICT_NAME")) && parentPath == "b" })
		assertTrue(uploads.single().second.path.matches(Regex("b/$CONFLICT_NAME")))
	}

	@Test
	fun `an upload whose name is taken in the cloud renames the file also if its folder has been moved since`() {
		givenUpload(remoteModified = null)
		whenever(dao.loadById(ID)).thenReturn(uploading().apply {
			path = "b/x.txt"
			parentPath = "b"
		})
		val taken = writeTarget("x.txt")
		whenever(repository.write(eq(taken), any(), any(), any(), any())).thenThrow(CloudNodeAlreadyExistsException("x.txt"))
		whenever(repository.file(eq(root), argThat { matches(CONFLICT_NAME) }, any())).thenReturn(mock())

		upload(base = 5_000)

		verify(dao).renamePending(eq(VAULT_ID), eq("b/x.txt"), argThat { matches(Regex("b/$CONFLICT_NAME")) }, argThat { matches(CONFLICT_NAME) }, any(), isNull())
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

		verify(dao).renamePending(eq(VAULT_ID), eq("a/x.txt"), argThat { matches(Regex("a/$CONFLICT_NAME")) }, argThat { matches(CONFLICT_NAME) }, any(), isNull())
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
		whenever(dao.loadById(ID)).thenReturn(uploading().apply { pendingDelete = 1 })

		val upload = upload(base = 5_000)

		verify(repository, never()).write(any(), any(), any(), any(), any())
		assertFailsWith<CancellationException>(upload.result)
	}

	@Test
	fun `a failed upload stays pending and reports why it failed`() {
		givenUpload(remoteModified = 5_400)
		whenever(repository.write(any(), any(), any(), any(), any())).thenThrow(NetworkConnectionException())
		writeTarget("x.txt")

		val upload = upload(base = 5_000)

		verify(dao, never()).markUploaded(any(), anyOrNull(), anyOrNull(), any())
		verify(dao, never()).discardPendingChange(any())
		verify(dao, never()).markSynced(any())
		assertFailsWith<NetworkConnectionException>(upload.result)
	}

	@Test
	fun `a retry overwrites what a failed upload of the file wrote to the cloud instead of keeping it as a conflict`() {
		val target = failedFirstUpload()
		// the cloud took the failed upload, e.g. only its response got lost
		val written = remoteFile(8_000, localFile.length())
		whenever(repository.metadata(any())).thenReturn(written)

		upload(base = 5_000)

		verify(repository, times(2)).write(eq(target), any(), any(), eq(true), any())
		verify(dao, never()).store(any())
		verify(dao).markUploaded(ID, 3, 9_000, GENERATION + 1)
	}

	@Test
	fun `a retry keeps a change made elsewhere after a failed upload as a conflict`() {
		failedFirstUpload()
		val changed = remoteFile(8_000, localFile.length() + 11)
		whenever(repository.metadata(any())).thenReturn(changed)
		whenever(repository.file(eq(root), argThat { matches(CONFLICT_NAME) }, any())).thenReturn(mock())

		upload(base = 5_000)

		verify(dao).store(argThat { path.matches(Regex("a/$CONFLICT_NAME")) })
	}

	@Test
	fun `a retry overwrites what the first failed upload wrote also after a later retry failed again`() {
		val target = failedFirstUpload(failures = 2)
		val written = remoteFile(8_000, localFile.length())
		whenever(repository.metadata(any())).thenReturn(written)
		upload(base = 5_000)

		upload(base = 5_000)

		verify(repository, times(3)).write(eq(target), any(), any(), eq(true), any())
		verify(dao, never()).store(any())
	}

	@Test
	fun `a retry that fails before it writes keeps recognising what the earlier failed upload wrote`() {
		val target = failedFirstUpload()
		doThrow(NetworkConnectionException()).whenever(repository).metadata(any())
		upload(base = 5_000)
		// the cloud took the first upload
		val written = remoteFile(8_000, localFile.length())
		doReturn(written).whenever(repository).metadata(any())

		upload(base = 5_000)

		verify(repository, times(2)).write(eq(target), any(), any(), eq(true), any())
		verify(dao, never()).store(any())
	}

	@Test
	fun `a retry of a new file overwrites what its failed upload wrote under its name instead of renaming it`() {
		val target = failedFirstUpload(remoteModified = null, lastModified = null, base = null)
		verify(repository).write(eq(target), any(), any(), eq(false), any())
		val written = remoteFile(8_000, localFile.length())
		whenever(repository.metadata(any())).thenReturn(written)

		upload(base = null)

		verify(repository).write(eq(target), any(), any(), eq(true), any())
	}

	@Test
	fun `a retry of a new file doesn't take a file of the same size but other content that was in the cloud before its failed upload for its own`() {
		localFile.writeText("local-content")
		givenCloudContent("other-content")
		val target = failedFirstUpload(remoteSize = localFile.length(), lastModified = null, base = null)

		upload(base = null)

		verify(repository, times(2)).write(eq(target), any(), any(), eq(false), any())
		verify(repository, never()).write(any(), any(), any(), eq(true), any())
	}

	@Test
	fun `a retry overwrites what a failed upload under the name it was renamed to wrote, instead of renaming it again`() {
		givenUpload(remoteModified = null)
		val taken = writeTarget("x.txt")
		whenever(repository.write(eq(taken), any(), any(), any(), any())).thenThrow(CloudNodeAlreadyExistsException("x.txt"))
		val renamedTarget: CloudFile = mock()
		whenever(repository.file(eq(root), argThat { matches(CONFLICT_NAME) }, any())).thenReturn(renamedTarget)
		whenever(repository.write(eq(renamedTarget), any(), any(), any(), any())).thenThrow(NetworkConnectionException()).thenReturn(cryptoFile("a/x.txt", size = 3, modified = 9_000))
		upload(base = 5_000)
		val renamedPath = argumentCaptor<String>()
		verify(dao).renamePending(eq(VAULT_ID), eq("a/x.txt"), renamedPath.capture(), any(), any(), isNull())
		// the cloud took the failed upload under the new name, which the cache now lists the file under
		val renamed = path(renamedPath.firstValue)
		whenever(dao.load(VAULT_ID, renamed.path)).thenAnswer { dao.load(VAULT_ID, "a/x.txt") }
		val renamedHandle: CloudFile = mock()
		val written = remoteFile(8_000, localFile.length())
		whenever(repository.file(root, renamed.name)).thenReturn(renamedHandle)
		whenever(repository.metadata(renamedHandle)).thenReturn(written)

		inTest.upload(renamed, localFile, { null }) { _, _, _, _ -> }

		verify(repository).write(eq(renamedTarget), any(), any(), eq(true), any())
	}

	@Test
	fun `a retry overwrites what an earlier failed upload wrote once it landed late, also if the file was saved again in between`() {
		val firstSize = localFile.length()
		val target = failedFirstUpload(failures = 2)
		localFile.appendText(" saved again")
		// the cloud still holds the first upload
		upload(base = 5_000)
		// and takes it now
		val written = remoteFile(8_000, firstSize)
		whenever(repository.metadata(any())).thenReturn(written)

		upload(base = 5_000)

		verify(repository, times(3)).write(eq(target), any(), any(), eq(true), any())
		verify(dao, never()).store(any())
	}

	@Test
	fun `an upload that succeeded ends the recognition of its failed attempts, so that a later change of the same size but other content is a conflict`() {
		localFile.writeText("local-content")
		failedFirstUpload()
		val written = remoteFile(8_000, localFile.length())
		whenever(repository.metadata(any())).thenReturn(written)
		upload(base = 5_000)
		val changed = remoteFile(10_000, localFile.length())
		whenever(repository.metadata(any())).thenReturn(changed)
		givenCloudContent("x".repeat(localFile.length().toInt()))
		whenever(repository.file(eq(root), argThat { matches(CONFLICT_NAME) }, any())).thenReturn(mock())

		upload(base = 9_000)

		verify(dao).store(argThat { path.matches(Regex("a/$CONFLICT_NAME")) })
	}

	@Test
	fun `an abandoned upload ends the recognition of its failed attempts`() {
		localFile.writeText("local-content")
		failedFirstUpload()
		inTest.abandonUpload(path("a/x.txt"))
		val changed = remoteFile(8_000, localFile.length())
		whenever(repository.metadata(any())).thenReturn(changed)
		givenCloudContent("x".repeat(localFile.length().toInt()))
		whenever(repository.file(eq(root), argThat { matches(CONFLICT_NAME) }, any())).thenReturn(mock())

		upload(base = 5_000)

		verify(dao).store(argThat { path.matches(Regex("a/$CONFLICT_NAME")) })
	}

	@Test
	fun `an upload of a local file is known from its scheduling until it has ended`() {
		givenUpload(remoteModified = 5_400)
		writeTarget("x.txt")
		val uploadExecutor = QueueExecutor()
		inTest = cache(CloudTaskScheduler(direct, uploadExecutor, direct))

		val upload = upload(base = 5_000)
		assertTrue(inTest.hasUploadOf(localFile))
		assertFalse(inTest.hasUploadOf(File(localFile.parentFile, "other")))

		uploadExecutor.runAll()
		assertTrue(upload.result.isDone)
		assertFalse(inTest.hasUploadOf(localFile))
	}

	@Test
	fun `an upload that a vault lock interrupts stays pending without being recorded or reported as failed`() {
		givenUpload(remoteModified = 5_400)
		writeTarget("x.txt")
		whenever(repository.metadata(any())).thenThrow(MissingCryptorException())
		val failed = mutableListOf<Throwable>()
		events.addListener(object : VaultDocumentListener {
			override fun onChanged(folder: VaultPath) {}
			override fun onChangeFailed(path: VaultPath, error: Throwable, userInitiated: Boolean) {
				failed.add(error)
			}
		})

		val upload = inTest.upload(path("a/x.txt"), localFile, { 5_000 }, userInitiated = true) { _, _, _, _ -> }

		assertFailsWith<CancellationException>(upload.result)
		assertNull(inTest.uploadFailure(uploading()))
		assertEquals(emptyList<Throwable>(), failed)
		verify(dao, never()).markSynced(any())
	}

	@Test
	fun `an upload reports its progress, also to the listeners, and gives the cloud the modification date it was made with`() {
		givenUpload(remoteModified = 5_400)
		val target = writeTarget("x.txt")
		val progress: ProgressAware<UploadState> = mock()
		val reported = Progress.started(UploadState.upload(target))
		val reportedToListeners = mutableListOf<Pair<VaultPath, Progress<UploadState>>>()
		events.addListener(object : VaultDocumentListener {
			override fun onChanged(folder: VaultPath) {}
			override fun onUploadProgress(path: VaultPath, progress: Progress<UploadState>) {
				reportedToListeners.add(path to progress)
			}
		})
		var modified: Date? = null
		whenever(repository.write(eq(target), any(), any(), any(), any())).thenAnswer {
			modified = it.getArgument<DataSource>(1).modifiedDate(mock()).get()
			it.getArgument<ProgressAware<UploadState>>(2).onProgress(reported)
			cryptoFile("a/uploaded.txt", size = 3, modified = 9_000)
		}

		inTest.upload(path("a/x.txt"), localFile, { 5_000 }, progress, modifiedDate = 4_000) { _, _, _, _ -> }

		assertEquals(Date(4_000), modified)
		verify(progress).onProgress(reported)
		assertEquals(listOf(path("a/x.txt") to reported), reportedToListeners)
	}

	@Test
	fun `a failed upload is recorded for its write and reported to the listeners until the file is uploaded again`() {
		givenUpload(remoteModified = 5_400)
		writeTarget("x.txt")
		val failure = NetworkConnectionException()
		val uploaded = cryptoFile("a/x.txt", size = 3, modified = 9_000)
		whenever(repository.write(any(), any(), any(), any(), any())).thenThrow(failure).thenReturn(uploaded)
		val failed = mutableListOf<Triple<VaultPath, Throwable, Boolean>>()
		events.addListener(object : VaultDocumentListener {
			override fun onChanged(folder: VaultPath) {}
			override fun onChangeFailed(path: VaultPath, error: Throwable, userInitiated: Boolean) {
				failed.add(Triple(path, error, userInitiated))
			}
		})

		inTest.upload(path("a/x.txt"), localFile, { 5_000 }, userInitiated = true) { _, _, _, _ -> }

		assertSame(failure, inTest.uploadFailure(uploading()))
		assertNull(inTest.uploadFailure(uploading().apply { uploadGeneration = GENERATION + 1 }))
		assertEquals(listOf(Triple(path("a/x.txt"), failure as Throwable, true)), failed)

		inTest.upload(path("a/x.txt"), localFile, { 5_000 }) { _, _, _, _ -> }
		assertNull(inTest.uploadFailure(uploading()))
	}

	@Test
	fun `a change the cloud rejects is reported to the listeners, telling whether the user made it`() {
		whenever(dao.load(VAULT_ID, "")).thenReturn(null)
		whenever(dao.insert(any())).thenReturn(ID)
		whenever(repository.folder(eq(root), any())).thenReturn(mock())
		whenever(repository.create(any<CloudFolder>())).thenThrow(NetworkConnectionException())
		val failed = mutableListOf<Pair<VaultPath, Boolean>>()
		events.addListener(object : VaultDocumentListener {
			override fun onChanged(folder: VaultPath) {}
			override fun onChangeFailed(path: VaultPath, error: Throwable, userInitiated: Boolean) {
				failed.add(path to userInitiated)
			}
		})

		inTest.create(VaultPath(vault), "new", isFolder = true, userInitiated = true)
		inTest.create(VaultPath(vault), "other", isFolder = true)
		listings.runAll()

		assertEquals(listOf(path("new") to true, path("other") to false), failed)
	}

	@Test
	fun `a listing that needs an authentication is reported also for a folder that has been listed before`() {
		whenever(dao.load(VAULT_ID, "a")).thenReturn(entity("a", Document.MIME_TYPE_DIR).apply { lastEnumeratedAt = now - 60_000 })
		whenever(repository.resolve(eq(cryptoCloud), any())).thenReturn(root)
		val authFailure = NoAuthenticationProvidedException(cryptoCloud)
		whenever(repository.list(root)).thenThrow(authFailure)

		inTest.children(path("a"))
		listings.runAll()
		val children = inTest.children(path("a"))

		assertSame(authFailure, children.authError)
		assertNull(children.error)
	}

	@Test
	fun `the uploads below a move the process ended during wait until both of its folders have been listed`() {
		val moved = entity("b/x.txt").apply {
			sourcePath = "a/x.txt"
			status = DocumentMetadataEntity.STATUS_UPLOADING
		}
		whenever(dao.loadInterruptedChanges()).thenReturn(listOf(moved))
		whenever(dao.hasPendingUploadInSubtree(VAULT_ID, "b/x.txt")).thenReturn(true)
		whenever(dao.loadById(ID)).thenReturn(moved)
		whenever(dao.loadPendingUploads(VAULT_ID)).thenReturn(listOf(moved))
		val folderA: CloudFolder = mock()
		val folderB: CloudFolder = mock()
		whenever(repository.resolve(cryptoCloud, "/a")).thenReturn(folderA)
		whenever(repository.resolve(cryptoCloud, "/b")).thenReturn(folderB)
		whenever(repository.list(folderA)).thenReturn(emptyList())
		val listedAtTarget = listOf<CloudNode>(cryptoFile("b/x.txt"))
		whenever(repository.list(folderB)).thenReturn(listedAtTarget)
		val resolved = mutableListOf<Vault>()
		val inTest = cache(CloudTaskScheduler(listings, direct, direct)).apply { onInterruptedMovesResolved = { resolved.add(it) } }

		assertEquals(emptyList<VaultPath>(), inTest.pendingUploads(vault))
		verify(dao, never()).discardPendingChange(any())
		inTest.resolveInterruptedMoves(vault)
		listings.runAll()

		verify(dao).clearSourcePath(ID)
		verify(dao, never()).revertMove(any(), any())
		assertEquals(listOf(vault), resolved)
		assertEquals(listOf(path("b/x.txt")), inTest.pendingUploads(vault))
	}

	@Test
	fun `a move the process ended during is settled only once its target folder has been listed, too`() {
		val moved = givenInterruptedMove()
		whenever(dao.load(VAULT_ID, "a")).thenReturn(entity("a", Document.MIME_TYPE_DIR))
		whenever(dao.load(VAULT_ID, "b")).thenReturn(entity("b", Document.MIME_TYPE_DIR))
		val folderA: CloudFolder = mock()
		val folderB: CloudFolder = mock()
		whenever(repository.resolve(cryptoCloud, "/a")).thenReturn(folderA)
		whenever(repository.resolve(cryptoCloud, "/b")).thenReturn(folderB)
		whenever(repository.list(folderA)).thenReturn(emptyList())
		val listedAtTarget = listOf<CloudNode>(cryptoFile("b/x.txt"))
		whenever(repository.list(folderB)).thenReturn(listedAtTarget)
		val inTest = cache(CloudTaskScheduler(listings, direct, direct))
		whenever(dao.load(VAULT_ID, "b/x.txt")).thenReturn(moved)

		inTest.children(path("a"))
		listings.runAll()

		verify(dao, never()).clearSourcePath(any())
		verify(dao, never()).revertMove(any(), any())
		assertEquals(emptyList<VaultPath>(), inTest.pendingUploads(vault))

		inTest.children(path("b"))
		listings.runAll()

		verify(dao).clearSourcePath(ID)
		assertEquals(listOf(path("b/x.txt")), inTest.pendingUploads(vault))
	}

	@Test
	fun `an upload below a move the process ended during waits for the move to be settled, also if a writer closed the file`() {
		val moved = givenInterruptedMove()
		whenever(dao.load(VAULT_ID, "b/x.txt")).thenReturn(moved)
		val inTest = cache(CloudTaskScheduler(listings, direct, direct))

		val upload = inTest.upload(path("b/x.txt"), localFile, { 5_000 }) { _, _, _, _ -> }
		listings.runAll()

		assertThrows<CancellationException> { upload.result.get() }
		verify(repository, never()).write(any(), any(), any(), any(), any())
	}

	@Test
	fun `the uploads below a folder whose move the process ended during wait for the move to be settled`() {
		val movedFolder = entity("b/sub", Document.MIME_TYPE_DIR).apply { sourcePath = "a/sub" }
		whenever(dao.loadInterruptedChanges()).thenReturn(listOf(movedFolder))
		whenever(dao.hasPendingUploadInSubtree(VAULT_ID, "b/sub")).thenReturn(true)
		whenever(dao.loadById(ID)).thenReturn(movedFolder)
		whenever(dao.loadPendingUploads(VAULT_ID)).thenReturn(listOf(entity("b/sub/x.txt").apply { status = DocumentMetadataEntity.STATUS_UPLOADING }, entity("b/y.txt").apply { status = DocumentMetadataEntity.STATUS_UPLOADING }))

		val inTest = cache(CloudTaskScheduler(listings, direct, direct))

		assertEquals(listOf(path("b/y.txt")), inTest.pendingUploads(vault))
	}

	@Test
	fun `a move the process ended during stays where the cache has it if the listings find the node at neither or both of its folders`() {
		listOf(false, true).forEach { foundAtBoth ->
			clearInvocations(dao)
			val moved = givenInterruptedMove()
			val folderA: CloudFolder = mock()
			val folderB: CloudFolder = mock()
			whenever(repository.resolve(cryptoCloud, "/a")).thenReturn(folderA)
			whenever(repository.resolve(cryptoCloud, "/b")).thenReturn(folderB)
			val listedAtSource = if (foundAtBoth) listOf<CloudNode>(cryptoFile("a/x.txt")) else emptyList()
			val listedAtTarget = if (foundAtBoth) listOf<CloudNode>(cryptoFile("b/x.txt")) else emptyList()
			whenever(repository.list(folderA)).thenReturn(listedAtSource)
			whenever(repository.list(folderB)).thenReturn(listedAtTarget)
			val inTest = cache(CloudTaskScheduler(listings, direct, direct))

			inTest.resolveInterruptedMoves(vault)
			listings.runAll()

			verify(dao).clearSourcePath(requireNotNull(moved.id))
			verify(dao, never()).revertMove(any(), any())
		}
	}

	@Test
	fun `a move the process ended during without a pending upload below it is dropped when the cache is created`() {
		val moved = entity("b/x.txt").apply { sourcePath = "a/x.txt" }
		whenever(dao.loadInterruptedChanges()).thenReturn(listOf(moved))
		whenever(dao.hasPendingUploadInSubtree(VAULT_ID, "b/x.txt")).thenReturn(false)

		cache(CloudTaskScheduler(listings, direct, direct))

		verify(dao).discardPendingChange(moved)
	}

	@Test
	fun `a move the process ended during before it reached the cloud is undone once both of its folders have been listed`() {
		val moved = entity("b/x.txt").apply {
			sourcePath = "a/x.txt"
			status = DocumentMetadataEntity.STATUS_UPLOADING
		}
		whenever(dao.loadInterruptedChanges()).thenReturn(listOf(moved))
		whenever(dao.hasPendingUploadInSubtree(VAULT_ID, "b/x.txt")).thenReturn(true)
		whenever(dao.loadById(ID)).thenReturn(moved)
		val folderA: CloudFolder = mock()
		val folderB: CloudFolder = mock()
		whenever(repository.resolve(cryptoCloud, "/a")).thenReturn(folderA)
		whenever(repository.resolve(cryptoCloud, "/b")).thenReturn(folderB)
		val listedAtSource = listOf<CloudNode>(cryptoFile("a/x.txt"))
		whenever(repository.list(folderA)).thenReturn(listedAtSource)
		whenever(repository.list(folderB)).thenReturn(emptyList())
		val inTest = cache(CloudTaskScheduler(listings, direct, direct))

		inTest.resolveInterruptedMoves(vault)
		listings.runAll()

		verify(dao).revertMove(eq(ID), any())
	}

	@Test
	fun `a write that can't take its file's place gets a conflict name next to it`() {
		whenever(dao.load(VAULT_ID, "a/x.txt")).thenReturn(entity("a/x.txt"))

		val conflictPath = inTest.createConflictCopy(path("a/x.txt"))

		assertTrue(conflictPath.path.matches(Regex("a/$CONFLICT_NAME")))
		verify(dao).insert(argThat { path == conflictPath.path && parentPath == "a" && status == DocumentMetadataEntity.STATUS_UPLOADING && lastModified == null })
		assertEquals(listOf(path("a")), changedFolders)
	}

	@Test
	fun `an upload reports where the file was uploaded to`() {
		givenUpload(remoteModified = 5_400)
		writeTarget("x.txt")

		val upload = upload(base = 5_000)

		assertEquals(path("a/x.txt"), upload.result.get().path)
		assertEquals(9_000L, upload.result.get().node.modified?.time)
	}

	@Test
	fun `an upload whose name is taken in the cloud reports the conflict name`() {
		givenUpload(remoteModified = null)
		var renamedTo: String? = null
		doAnswer { renamedTo = it.getArgument(2) }.whenever(dao).renamePending(any(), any(), any(), any(), any(), anyOrNull())
		whenever(dao.loadById(ID)).thenAnswer { uploading().apply { renamedTo?.let { path = it } } }
		val taken = writeTarget("x.txt")
		whenever(repository.write(eq(taken), any(), any(), any(), any())).thenThrow(CloudNodeAlreadyExistsException("x.txt"))
		whenever(repository.file(eq(root), argThat { matches(CONFLICT_NAME) }, any())).thenReturn(mock())

		val upload = upload(base = 5_000)

		assertTrue(upload.result.get().path.path.matches(Regex("a/$CONFLICT_NAME")))
	}

	@Test
	fun `an upload superseded by a later write ends like the upload of that write`() {
		val uploadExecutor = QueueExecutor()
		inTest = cache(CloudTaskScheduler(direct, uploadExecutor, direct))
		givenUpload(remoteModified = 5_400)
		writeTarget("x.txt")

		val superseded = upload(base = 5_000)
		val latest = upload(base = 5_000)
		uploadExecutor.runAll()

		verify(repository).write(any(), any(), any(), any(), any())
		assertEquals(latest.result.get(), superseded.result.get())
	}

	@Test
	fun `an upload tells whether its copy is still the latest write of the file`() {
		listOf(5_000L, 7_000L).forEach { remoteModified ->
			listOf(false, true).forEach { writtenAgain ->
				givenUpload(remoteModified = remoteModified)
				whenever(repository.file(any(), any(), any())).thenReturn(mock())
				whenever(repository.write(any(), any(), any(), any(), any())).thenAnswer {
					if (writtenAgain) {
						dao.markUploading(VAULT_ID, "a/x.txt")
					}
					cryptoFile("a/uploaded.txt", size = 3, modified = 9_000)
				}
				var isLatestWrite: Boolean? = null

				inTest.upload(path("a/x.txt"), localFile, { 5_000 }) { _, _, _, latest -> isLatestWrite = latest }

				assertEquals(!writtenAgain, isLatestWrite)
			}
		}
	}

	@Test
	fun `an upload is in flight from when it is scheduled until it ends`() {
		val uploadExecutor = QueueExecutor()
		inTest = cache(CloudTaskScheduler(direct, uploadExecutor, direct))
		givenUpload(remoteModified = 5_400)
		writeTarget("x.txt")
		whenever(repository.write(any(), any(), any(), any(), any())).thenThrow(NetworkConnectionException())

		upload(base = 5_000)

		assertTrue(inTest.hasUploadInFlight(path("a/x.txt")))
		assertTrue(inTest.hasUploads())
		uploadExecutor.runAll()
		assertFalse(inTest.hasUploadInFlight(path("a/x.txt")))
		assertFalse(inTest.hasUploads())
		upload(base = 5_000)
		inTest.markPendingUpload(path("a/x.txt"))
		assertFalse(inTest.hasUploadInFlight(path("a/x.txt")))
	}

	@Test
	fun `an upload hasn't ended until its end is recorded for the local copy, also once its file is synced`() {
		givenUpload(remoteModified = 5_000)
		writeTarget("x.txt")
		var synced = false
		whenever(dao.load(VAULT_ID, "a/x.txt")).thenAnswer { if (synced) entity("a/x.txt") else uploading() }
		doAnswer { synced = true }.whenever(dao).markUploaded(any(), anyOrNull(), anyOrNull(), any())
		var notEndedWhileRecorded: Boolean? = null

		inTest.upload(path("a/x.txt"), localFile, { 5_000L }) { _, _, _, _ ->
			notEndedWhileRecorded = inTest.hasUploadNotEnded(path("a/x.txt"))
		}.result.get()

		assertTrue(synced)
		assertEquals(true, notEndedWhileRecorded)
		assertFalse(inTest.hasUploadNotEnded(path("a/x.txt")))
	}

	@Test
	fun `deleting a folder stops the running uploads below it, also of writes that have been superseded`() {
		givenUpload(remoteModified = null)
		writeTarget("x.txt")
		whenever(dao.load(VAULT_ID, "a")).thenReturn(entity("a", Document.MIME_TYPE_DIR).apply { id = ID + 1 })
		var transferStopped = false
		whenever(repository.write(any(), any(), any(), any(), any())).thenAnswer {
			upload(base = 5_000)
			inTest.delete(path("a"))
			transferStopped = runCatching { it.getArgument<DataSource>(1).open(mock()) }.exceptionOrNull() is org.cryptomator.domain.exception.CancellationException
			throw org.cryptomator.domain.exception.CancellationException()
		}

		val superseded = upload(base = 5_000)

		assertTrue(transferStopped)
		assertFailsWith<CancellationException>(superseded.result)
		verify(dao).markPendingDelete(VAULT_ID, "a", 1)
	}

	@Test
	fun `an upload of a file that is gone before it starts is canceled`() {
		val uploadExecutor = QueueExecutor()
		inTest = cache(CloudTaskScheduler(direct, uploadExecutor, direct))
		givenUpload(remoteModified = 5_400)
		val upload = upload(base = 5_000)
		whenever(dao.loadById(ID)).thenReturn(null)

		uploadExecutor.runAll()

		verify(repository, never()).write(any(), any(), any(), any(), any())
		assertFailsWith<CancellationException>(upload.result)
	}

	@Test
	@Timeout(10)
	fun `an upload whose file can't be read from the cache ends with the error`() {
		val uploadExecutor = QueueExecutor()
		inTest = cache(CloudTaskScheduler(direct, uploadExecutor, direct))
		givenUpload(remoteModified = 5_400)
		val upload = upload(base = 5_000)
		whenever(dao.loadVisibleById(ID)).thenThrow(IllegalStateException("disk I/O error"))

		uploadExecutor.runAll()

		assertFailsWith<IllegalStateException>(upload.result)
		assertFalse(inTest.hasUploadInFlight(path("a/x.txt")))
	}

	@Test
	fun `an upload goes to the path it was requested for, also if the file has been moved since`() {
		// requested for a/y.txt, but the move there was rejected and undone meanwhile
		whenever(dao.load(VAULT_ID, "a/y.txt")).thenReturn(uploading().apply {
			path = "a/y.txt"
			name = "y.txt"
		})
		whenever(dao.loadById(ID)).thenReturn(uploading())
		whenever(repository.file(root, "y.txt")).thenReturn(mock())
		val target = writeTarget("y.txt")
		val uploaded = cryptoFile("a/y.txt")
		whenever(repository.write(eq(target), any(), any(), any(), any())).thenReturn(uploaded)

		val upload = inTest.upload(path("a/y.txt"), localFile, { 5_000 }) { _, _, _, _ -> }

		upload.result.get()
		verify(repository).write(eq(target), any(), any(), eq(false), any())
		verify(repository, never()).file(eq(root), eq("x.txt"), any())
	}

	@Test
	fun `deleting a folder stops only the uploads below it`() {
		val uploadExecutor = QueueExecutor()
		inTest = cache(CloudTaskScheduler(listings, uploadExecutor, direct))
		val other = entity("ab/y.txt").apply {
			id = ID + 1
			status = DocumentMetadataEntity.STATUS_UPLOADING
			uploadGeneration = GENERATION
		}
		whenever(dao.load(VAULT_ID, "a/x.txt")).thenReturn(uploading())
		whenever(dao.load(VAULT_ID, "ab/y.txt")).thenReturn(other)
		whenever(dao.loadById(ID)).thenReturn(uploading())
		whenever(dao.loadById(ID + 1)).thenReturn(other)
		whenever(dao.load(VAULT_ID, "a")).thenReturn(entity("a", Document.MIME_TYPE_DIR).apply { id = ID + 2 })
		val below = inTest.upload(path("a/x.txt"), localFile, { 5_000 }) { _, _, _, _ -> }
		val beside = inTest.upload(path("ab/y.txt"), localFile, { 5_000 }) { _, _, _, _ -> }

		inTest.delete(path("a"))

		assertTrue(below.isStopped)
		assertFalse(beside.isStopped)
	}

	@Test
	fun `a folder that contains a node being deleted can't be moved`() {
		whenever(dao.load(VAULT_ID, "a")).thenReturn(entity("a", Document.MIME_TYPE_DIR))
		whenever(dao.hasPendingDeleteInSubtree(VAULT_ID, "a")).thenReturn(true)

		assertThrows<IllegalStateException> { inTest.move(path("a"), VaultPath(vault), "b") }

		verify(dao, never()).move(any(), any(), any(), any(), any(), any())
	}

	@Test
	fun `deleting a file stops its running upload but keeps it pending until the cloud deleted the file`() {
		givenUpload(remoteModified = null)
		val target = writeTarget("x.txt")
		val deletion = CompletableFuture<DocumentMetadataCache.Deletion>()
		var transferStopped = false
		whenever(repository.write(eq(target), any(), any(), any(), any())).thenAnswer {
			whenever(dao.loadById(ID)).thenReturn(uploading().apply { pendingDelete = 1 })
			deletion.complete(inTest.delete(path("a/x.txt")))
			val data = it.getArgument<DataSource>(1)
			transferStopped = runCatching { data.open(mock()) }.exceptionOrNull() is org.cryptomator.domain.exception.CancellationException
			throw org.cryptomator.domain.exception.CancellationException()
		}
		whenever(repository.file(eq(root), eq("x.txt"))).thenReturn(mock())

		val upload = upload(base = 5_000)

		assertTrue(transferStopped)
		assertFailsWith<CancellationException>(upload.result)
		verify(dao, never()).markSynced(any())
		verify(dao, never()).deleteSubtree(any(), any())
		listings.runAll()
		deletion.get().result.get()
		verify(dao).deleteSubtree(VAULT_ID, "a/x.txt")
	}

	@Test
	fun `deleting a file whose first upload it stopped deletes what the upload put into the cloud anyway`() {
		val file = stoppedFirstUpload()
		// the cloud took the upload although its response got lost
		val landed = remoteFile(8_000, localFile.length())
		whenever(repository.metadata(file)).thenReturn(landed)
		givenCloudContent("new")

		listings.runAll()

		verify(repository).delete(file)
		verify(dao).deleteSubtree(VAULT_ID, "a/x.txt")
		// a listing that ran meanwhile may have taken the file for a node of its own
		verify(dao).updateLastEnumeratedAt(VAULT_ID, "a", null)
	}

	@Test
	fun `deleting a file whose first upload it stopped leaves a cloud file alone that was replaced while it was compared`() {
		val file = stoppedFirstUpload()
		val landed = remoteFile(8_000, localFile.length())
		val replaced = remoteFile(9_000, localFile.length())
		whenever(repository.metadata(file)).thenReturn(landed, replaced)
		givenCloudContent("new")

		listings.runAll()

		verify(repository).read(eq(landed), any(), any())
		verify(repository, never()).delete(any<CloudFile>())
	}

	@Test
	fun `deleting a file whose first upload it stopped deletes what the upload sent, not what its copy holds since`() {
		val file = stoppedFirstUpload(changedAfterSend = "xyz")
		val landed = remoteFile(8_000, localFile.length())
		whenever(repository.metadata(file)).thenReturn(landed)
		givenCloudContent("new")

		listings.runAll()

		verify(repository).delete(file)
	}

	@Test
	fun `deleting a file after several failed uploads deletes what the first of them put into the cloud`() {
		localFile.writeText("new")
		failedFirstUpload(failures = 2, remoteModified = null, lastModified = null, base = null)
		localFile.writeText("two")
		upload(base = null)
		val file = repository.file(root, "x.txt")
		val landed = remoteFile(8_000, localFile.length())
		whenever(repository.metadata(file)).thenReturn(landed)
		givenCloudContent("new")
		whenever(dao.loadById(ID)).thenReturn(uploading().apply { pendingDelete = 1 })

		inTest.delete(path("a/x.txt"))
		listings.runAll()

		verify(repository).delete(file)
	}

	@Test
	fun `deleting a file after several failed uploads deletes what the latest of them put into the cloud`() {
		localFile.writeText("new")
		failedFirstUpload(failures = 2, remoteModified = null, lastModified = null, base = null)
		localFile.writeText("two")
		upload(base = null)
		val file = repository.file(root, "x.txt")
		val landed = remoteFile(8_000, localFile.length())
		whenever(repository.metadata(file)).thenReturn(landed)
		givenCloudContent("two")
		whenever(dao.loadById(ID)).thenReturn(uploading().apply { pendingDelete = 1 })

		inTest.delete(path("a/x.txt"))
		listings.runAll()

		verify(repository).delete(file)
	}

	@Test
	fun `deleting a file whose first upload it stopped leaves a cloud file with other content alone`() {
		val file = stoppedFirstUpload()
		// another device put a file of the same size there
		val other = remoteFile(8_000, localFile.length())
		whenever(repository.metadata(file)).thenReturn(other)
		givenCloudContent("xyz")

		listings.runAll()

		verify(repository).read(eq(other), any(), any())
		verify(repository, never()).delete(any<CloudFile>())
		verify(dao).deleteSubtree(VAULT_ID, "a/x.txt")
	}

	@Test
	fun `deleting a file whose first upload it stopped before it was sent leaves the cloud file alone`() {
		val file = stoppedFirstUpload(sendIncomplete = true)
		val landed = remoteFile(8_000, 3)
		whenever(repository.metadata(file)).thenReturn(landed)
		givenCloudContent("new")

		listings.runAll()

		// the upload asked before it started, the cleanup asks again
		verify(repository, times(2)).metadata(file)
		verify(repository, never()).delete(any<CloudFile>())
		verify(dao).deleteSubtree(VAULT_ID, "a/x.txt")
	}

	@Test
	fun `deleting a file whose first upload it stopped leaves a cloud file the upload can't have written alone`() {
		val file = stoppedFirstUpload()
		val other = remoteFile(8_000, localFile.length() + 11)
		whenever(repository.metadata(file)).thenReturn(other)
		givenCloudContent("new")

		listings.runAll()

		verify(repository, never()).delete(any<CloudFile>())
		verify(dao).deleteSubtree(VAULT_ID, "a/x.txt")
	}

	@Test
	fun `deleting a file whose first upload it stopped leaves a cloud file alone that was there before the upload`() {
		stoppedFirstUpload(remoteModified = 5_400)
		givenCloudContent("new")

		listings.runAll()

		verify(repository, never()).delete(any<CloudFile>())
		verify(dao).deleteSubtree(VAULT_ID, "a/x.txt")
	}

	@Test
	fun `deleting a file renamed during its stopped first upload leaves the node that took its old name alone`() {
		val file = stoppedFirstUpload(deleteAs = "a/y.txt")
		val landed = remoteFile(8_000, localFile.length())
		whenever(repository.metadata(file)).thenReturn(landed)
		val newNode = entity("a/x.txt").apply { id = ID + 1 }
		whenever(dao.load(VAULT_ID, "a/x.txt")).thenReturn(newNode)
		givenCloudContent("new")

		listings.runAll()

		verify(repository, never()).delete(any<CloudFile>())
		verify(dao).deleteSubtree(VAULT_ID, "a/y.txt")
	}

	@Test
	fun `deleting a file whose first upload it stopped succeeds although the cloud can't be reached`() {
		stoppedFirstUpload()
		whenever(repository.metadata(any())).thenThrow(NetworkConnectionException())

		listings.runAll()

		verify(repository, never()).delete(any<CloudFile>())
		verify(dao).deleteSubtree(VAULT_ID, "a/x.txt")
	}

	@Test
	fun `moving a file that has never been in the cloud deletes what its failed upload put into the cloud only once`() {
		localFile.writeText("new")
		failedFirstUpload(remoteModified = null, lastModified = null, base = null)
		val file = repository.file(root, "x.txt")
		val landed = remoteFile(8_000, localFile.length())
		whenever(repository.metadata(file)).thenReturn(landed)
		givenCloudContent("new")
		val target: CloudFile = mock()
		whenever(repository.file(eq(root), eq("y.txt"))).thenReturn(target)

		val move = inTest.move(path("a/x.txt"), VaultPath(vault), "y.txt")
		val moved = entity("y.txt").apply {
			sourcePath = "a/x.txt"
			status = DocumentMetadataEntity.STATUS_UPLOADING
		}
		whenever(dao.loadById(ID)).thenReturn(moved)
		listings.runAll()

		move.get()
		verify(repository).delete(file)
		verify(repository, never()).move(anyOrNull<CloudFile>(), anyOrNull<CloudFile>())
		verify(dao).clearSourcePath(ID)

		// deleting the file afterwards doesn't delete again what the move handled
		whenever(dao.load(VAULT_ID, "y.txt")).thenReturn(moved)
		whenever(dao.loadById(ID)).thenReturn(entity("y.txt").apply {
			status = DocumentMetadataEntity.STATUS_UPLOADING
			pendingDelete = 1
		})
		inTest.delete(path("y.txt"))
		listings.runAll()

		verify(repository, times(1)).delete(any<CloudFile>())
	}

	@Test
	fun `abandoning the upload of a file that has never been in the cloud leaves what the upload put there`() {
		localFile.writeText("local-content")
		failedFirstUpload(remoteModified = null, lastModified = null, base = null)
		val file = repository.file(root, "x.txt")
		val landed = remoteFile(8_000, localFile.length())
		whenever(repository.metadata(file)).thenReturn(landed)
		givenCloudContent("local-content")

		inTest.abandonUpload(path("a/x.txt"))
		listings.runAll()

		verify(dao).deleteSubtree(VAULT_ID, "a/x.txt")
		verify(repository, never()).delete(any<CloudFile>())
	}

	@Test
	fun `creating a folder whose name is taken in the cloud renames it to a conflict name`() {
		whenever(dao.insert(any())).thenReturn(ID)
		whenever(dao.loadById(ID)).thenReturn(entity("new", Document.MIME_TYPE_DIR).apply { status = DocumentMetadataEntity.STATUS_CREATING })
		val taken: CloudFolder = mock()
		val created = cryptoFolder("new (1A2B3)")
		whenever(repository.folder(root, "new")).thenReturn(taken)
		whenever(repository.create(any())).thenReturn(created)
		whenever(repository.create(taken)).thenThrow(CloudNodeAlreadyExistsException("new"))
		whenever(repository.folder(eq(root), argThat { matches(CONFLICT_FOLDER_NAME) })).thenReturn(mock())

		val creation = inTest.create(VaultPath(vault), "new", isFolder = true)
		listings.runAll()

		verify(dao).renamePending(eq(VAULT_ID), eq("new"), argThat { matches(CONFLICT_FOLDER_NAME) }, argThat { matches(CONFLICT_FOLDER_NAME) }, eq(Document.MIME_TYPE_DIR), isNull())
		verify(dao).markCreated(ID, null, null)
		verify(dao, never()).discardPendingChange(any())
		assertTrue(creation.result.get().path.path.matches(CONFLICT_FOLDER_NAME))
	}

	@Test
	fun `a change the cloud rejects is discarded`() {
		val pending = entity("new", Document.MIME_TYPE_DIR).apply { status = DocumentMetadataEntity.STATUS_CREATING }
		whenever(dao.insert(any())).thenReturn(ID)
		whenever(dao.loadById(ID)).thenReturn(pending)
		val folder: CloudFolder = mock()
		whenever(repository.folder(root, "new")).thenReturn(folder)
		whenever(repository.create(folder)).thenThrow(NetworkConnectionException())

		val creation = inTest.create(VaultPath(vault), "new", isFolder = true)
		changedFolders.clear()
		listings.runAll()

		verify(dao).discardPendingChange(pending)
		assertEquals(listOf(VaultPath(vault)), changedFolders)
		assertFailsWith<NetworkConnectionException>(creation.result)
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
		givenMoved("a.txt", "b.txt")
		val source: CloudFile = mock()
		val target: CloudFile = mock()
		whenever(repository.file(root, "a.txt")).thenReturn(source)
		whenever(repository.file(root, "b.txt")).thenReturn(target)

		val pausedWhileRecorded = mutableListOf<Boolean>()
		doAnswer { pausedWhileRecorded.add(changesPaused()) }.whenever(dao).clearSourcePath(any())

		inTest.move(path("a.txt"), VaultPath(vault), "b.txt")
		listings.runAll()

		verify(dao).move(VAULT_ID, "a.txt", "b.txt", "", "b.txt", "application/octet-stream")
		verify(repository).move(source, target)
		verify(dao).clearSourcePath(ID)
		assertEquals(listOf(true), pausedWhileRecorded)
	}

	@Test
	@Timeout(10)
	fun `a node moved again is moved on from where its earlier move left it once that is recorded`() {
		givenMovableNode("a.txt")
		val a: CloudFile = mock()
		val b: CloudFile = mock()
		val c: CloudFile = mock()
		whenever(repository.file(root, "a.txt")).thenReturn(a)
		whenever(repository.file(root, "b.txt")).thenReturn(b)
		whenever(repository.file(root, "c.txt")).thenReturn(c)

		inTest.move(path("a.txt"), VaultPath(vault), "b.txt")
		val second = inTest.move(path("b.txt"), VaultPath(vault), "c.txt")
		verify(dao, times(1)).move(any(), any(), any(), any(), any(), any())
		listings.runAll()

		second.get()
		inOrder(repository, dao) {
			verify(repository).move(a, b)
			verify(dao).clearSourcePath(ID)
			verify(dao).move(VAULT_ID, "b.txt", "c.txt", "", "c.txt", "application/octet-stream")
			verify(repository).move(b, c)
			verify(dao).clearSourcePath(ID)
		}
		verify(dao, never()).setSourcePath(any(), any())
	}

	@Test
	@Timeout(10)
	fun `a node moved three times before its first move is recorded is moved on in the order of the moves`() {
		givenMovableNode("a.txt")
		val a: CloudFile = mock()
		val b: CloudFile = mock()
		val c: CloudFile = mock()
		val d: CloudFile = mock()
		whenever(repository.file(root, "a.txt")).thenReturn(a)
		whenever(repository.file(root, "b.txt")).thenReturn(b)
		whenever(repository.file(root, "c.txt")).thenReturn(c)
		whenever(repository.file(root, "d.txt")).thenReturn(d)

		inTest.move(path("a.txt"), VaultPath(vault), "b.txt")
		val second = inTest.move(path("b.txt"), VaultPath(vault), "c.txt")
		// the node is still at b.txt in the cache, its second move waits for the first one
		val third = inTest.move(path("b.txt"), VaultPath(vault), "d.txt")
		listings.runAll()

		second.get()
		third.get()
		inOrder(repository, dao) {
			verify(repository).move(a, b)
			verify(dao).move(VAULT_ID, "b.txt", "c.txt", "", "c.txt", "application/octet-stream")
			verify(repository).move(b, c)
			verify(dao).move(VAULT_ID, "c.txt", "d.txt", "", "d.txt", "application/octet-stream")
			verify(repository).move(c, d)
		}
	}

	@Test
	@Timeout(10)
	fun `a node whose earlier move the cloud rejected is moved on from where that left it`() {
		givenMovableNode("a.txt")
		val a: CloudFile = mock()
		val b: CloudFile = mock()
		val c: CloudFile = mock()
		whenever(repository.file(root, "a.txt")).thenReturn(a)
		whenever(repository.file(root, "b.txt")).thenReturn(b)
		whenever(repository.file(root, "c.txt")).thenReturn(c)
		whenever(repository.move(a, b)).thenThrow(NetworkConnectionException())

		val first = inTest.move(path("a.txt"), VaultPath(vault), "b.txt")
		val second = inTest.move(path("b.txt"), VaultPath(vault), "c.txt")
		listings.runAll()

		assertThrows<ExecutionException> { first.get() }
		second.get()
		verify(repository).move(a, c)
	}

	@Test
	@Timeout(10)
	fun `a move of a node that is gone once its earlier move is recorded is given up without reporting it`() {
		givenMovableNode("a.txt")
		whenever(repository.file(eq(root), any())).thenReturn(mock())
		val failed = mutableListOf<Pair<VaultPath, Boolean>>()
		events.addListener(object : VaultDocumentListener {
			override fun onChanged(folder: VaultPath) {}
			override fun onChangeFailed(path: VaultPath, error: Throwable, userInitiated: Boolean) {
				failed.add(path to userInitiated)
			}
		})

		inTest.move(path("a.txt"), VaultPath(vault), "b.txt")
		val second = inTest.move(path("b.txt"), VaultPath(vault), "c.txt", userInitiated = true)
		whenever(dao.loadVisibleById(ID)).thenReturn(null)
		listings.runAll()

		assertThrows<CancellationException> { second.get() }
		assertEquals(emptyList<Pair<VaultPath, Boolean>>(), failed)
	}

	@Test
	@Timeout(10)
	fun `a move that can't be started once its earlier move is recorded is reported`() {
		givenMovableNode("a.txt")
		whenever(repository.file(eq(root), any())).thenReturn(mock())
		val failed = mutableListOf<Pair<VaultPath, Boolean>>()
		events.addListener(object : VaultDocumentListener {
			override fun onChanged(folder: VaultPath) {}
			override fun onChangeFailed(path: VaultPath, error: Throwable, userInitiated: Boolean) {
				failed.add(path to userInitiated)
			}
		})

		inTest.move(path("a.txt"), VaultPath(vault), "b.txt")
		val second = inTest.move(path("b.txt"), VaultPath(vault), "c.txt", userInitiated = true)
		whenever(dao.loadVisibleById(ID)).thenThrow(IllegalStateException("The database is closed"))
		listings.runAll()

		assertThrows<ExecutionException> { second.get() }
		assertEquals(listOf(path("c.txt") to true), failed.filter { (failedPath, _) -> failedPath == path("c.txt") })
	}

	@Test
	@Timeout(10)
	fun `a node moved again is moved on from the conflict name its earlier move got in the cloud`() {
		givenMovableNode("a.txt")
		whenever(repository.file(eq(root), any())).thenReturn(mock())
		val moved = cryptoFile("b.txt")
		whenever(repository.move(anyOrNull<CloudFile>(), anyOrNull<CloudFile>())).thenThrow(CloudNodeAlreadyExistsException("b.txt")).thenReturn(moved)

		inTest.move(path("a.txt"), VaultPath(vault), "b.txt")
		inTest.move(path("b.txt"), VaultPath(vault), "c.txt")
		listings.runAll()

		verify(dao).move(eq(VAULT_ID), argThat { matches(Regex("b \\([0-9A-F]{5}\\)\\.txt")) }, eq("c.txt"), eq(""), eq("c.txt"), any())
	}

	@Test
	fun `a move the cloud rejected doesn't keep a later move of the node marked as moving`() {
		whenever(dao.load(VAULT_ID, "a.txt")).thenReturn(entity("a.txt"))
		givenMoved("a.txt", "b.txt")
		val source: CloudFile = mock()
		val target: CloudFile = mock()
		whenever(repository.file(root, "a.txt")).thenReturn(source)
		whenever(repository.file(root, "b.txt")).thenReturn(target)
		val moved = cryptoFile("b.txt")
		whenever(repository.move(source, target)).thenThrow(NetworkConnectionException()).thenReturn(moved)

		inTest.move(path("a.txt"), VaultPath(vault), "b.txt")
		listings.runAll()
		inTest.move(path("a.txt"), VaultPath(vault), "b.txt")
		listings.runAll()

		verify(repository, times(2)).move(source, target)
		verify(dao).clearSourcePath(ID)
		verify(dao, never()).setSourcePath(any(), any())
	}

	@Test
	fun `a node is no longer marked as moving once its last move is done, also if a folder above it has been moved meanwhile`() {
		whenever(dao.load(VAULT_ID, "a")).thenReturn(entity("a", Document.MIME_TYPE_DIR))
		whenever(dao.load(VAULT_ID, "a/x.txt")).thenReturn(entity("a/x.txt"))
		// the folder a has been renamed to b in the cache before the move reached the cloud
		givenMoved("a/x.txt", "b/y.txt")
		whenever(repository.file(eq(root), any())).thenReturn(mock())

		inTest.move(path("a/x.txt"), path("a"), "y.txt")
		listings.runAll()

		verify(dao).clearSourcePath(ID)
		verify(dao, never()).setSourcePath(any(), any())
	}

	@Test
	fun `a folder that has never been listed is listed in the background`() {
		whenever(dao.load(VAULT_ID, "a")).thenReturn(entity("a", Document.MIME_TYPE_DIR))
		val listed = listOf(cryptoFile("a/x.txt"))
		whenever(repository.list(root)).thenReturn(listed)
		val pausedWhileStored = mutableListOf<Boolean>()
		doAnswer { pausedWhileStored.add(changesPaused()) }.whenever(dao).storeChildren(any(), any())

		val children = inTest.children(path("a"))

		assertTrue(children.isLoading)
		verify(repository, never()).list(any())
		listings.runAll()
		verify(dao).storeChildren(argThat { path == "a" && lastEnumeratedAt == now }, argThat { single().path == "a/x.txt" })
		assertEquals(listOf(true), pausedWhileStored)
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
	fun `a listing that ran after the vault was locked is no failed listing, isn't announced and is retried with the next listing`() {
		whenever(dao.load(VAULT_ID, "a")).thenReturn(entity("a", Document.MIME_TYPE_DIR))
		whenever(repository.list(root)).thenThrow(MissingCryptorException())
		inTest.children(path("a"))
		listings.runAll()
		// a listing the announcement caused would refresh the folder again, over and over
		assertEquals(emptyList<VaultPath>(), changedFolders)

		val children = inTest.children(path("a"))

		assertNull(children.error)
		assertTrue(children.isLoading)
		assertFalse(listings.isEmpty())
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

		assertEquals(2, listingsWhile("a") { upload(base = 5_000) })
	}

	@Test
	fun `creating a file never overwrites a file in the cloud`() {
		whenever(dao.insert(any())).thenReturn(ID)
		val target = writeTarget("new.txt", root)
		val createdFile = cryptoFile("new.txt", size = 0, modified = 4_000)
		whenever(repository.write(eq(target), any(), any(), any(), any())).thenReturn(createdFile)

		val documentId = inTest.create(VaultPath(vault), "new.txt", isFolder = false).documentId
		listings.runAll()

		assertEquals("$VAULT_ID:$ID", documentId)
		verify(repository).write(eq(target), any(), any(), eq(false), eq(0L))
		verify(dao).markCreated(ID, 0L, 4_000L)
	}

	@Test
	fun `a new file written before its creation starts is created by the upload of the write`() {
		whenever(dao.insert(any())).thenReturn(ID)
		whenever(dao.loadById(ID)).thenReturn(entity("new.txt").apply { status = DocumentMetadataEntity.STATUS_UPLOADING })

		val result = inTest.create(VaultPath(vault), "new.txt", isFolder = false).result
		listings.runAll()

		verify(repository, never()).write(any(), any(), any(), any(), any())
		verify(dao, never()).markCreated(any(), anyOrNull(), anyOrNull())
		assertFailsWith<CancellationException>(result)
	}

	@Test
	fun `a new file is not created empty while a writer has it open`() {
		whenever(dao.insert(any())).thenReturn(ID)
		val created = entity("new.txt").apply { status = DocumentMetadataEntity.STATUS_CREATING }
		val written = entity("new.txt").apply { status = DocumentMetadataEntity.STATUS_UPLOADING }
		var open = true
		inTest.isOpenForWriting = { id -> (id == ID && open).also { open = false } }
		whenever(dao.loadById(ID)).thenReturn(created, created, written)

		inTest.create(VaultPath(vault), "new.txt", isFolder = false)
		listings.runAll()

		verify(repository, never()).write(any(), any(), any(), any(), any())
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
		val pausedWhileRenamed = mutableListOf<Boolean>()
		doAnswer { pausedWhileRenamed.add(changesPaused()) }.whenever(dao).renamePending(any(), any(), any(), any(), any(), anyOrNull())

		inTest.create(VaultPath(vault), "new.txt", isFolder = false)
		listings.runAll()

		assertEquals(listOf(true), pausedWhileRenamed)
		verify(dao).renamePending(eq(VAULT_ID), eq("new.txt"), argThat { matches(CONFLICT_FILE_NAME) }, argThat { matches(CONFLICT_FILE_NAME) }, any(), isNull())
		verify(repository).write(eq(renamed), any(), any(), eq(false), any())
	}

	@Test
	fun `a node is moved to an unused name if the target name is taken in the cache`() {
		whenever(dao.load(VAULT_ID, "a.txt")).thenReturn(entity("a.txt"))
		whenever(dao.load(VAULT_ID, "b.txt")).thenReturn(entity("b.txt").apply { id = ID + 1 })
		givenMoved("a.txt", "b (1).txt")
		val source: CloudFile = mock()
		val target: CloudFile = mock()
		whenever(repository.file(root, "a.txt")).thenReturn(source)
		whenever(repository.file(root, "b (1).txt")).thenReturn(target)

		inTest.move(path("a.txt"), VaultPath(vault), "b.txt")
		listings.runAll()

		verify(dao).move(VAULT_ID, "a.txt", "b (1).txt", "", "b (1).txt", "application/octet-stream")
		verify(repository).move(source, target)
	}

	@Test
	fun `a file created under a name taken in the cache gets the next free number before its extension`() {
		whenever(dao.insert(any())).thenReturn(ID)
		whenever(dao.load(VAULT_ID, "new.txt")).thenReturn(entity("new.txt").apply { id = ID + 1 })
		whenever(dao.load(VAULT_ID, "new (1).txt")).thenReturn(entity("new (1).txt").apply { id = ID + 2 })

		inTest.create(VaultPath(vault), "new.txt", isFolder = false)

		verify(dao).insert(argThat { name == "new (2).txt" && path == "new (2).txt" })
	}

	@Test
	fun `a folder is moved as a folder`() {
		whenever(dao.load(VAULT_ID, "a")).thenReturn(entity("a", Document.MIME_TYPE_DIR))
		givenMoved("a", "b", Document.MIME_TYPE_DIR)
		val source: CloudFolder = mock()
		val target: CloudFolder = mock()
		whenever(repository.folder(root, "a")).thenReturn(source)
		whenever(repository.folder(root, "b")).thenReturn(target)

		inTest.move(path("a"), VaultPath(vault), "b")
		listings.runAll()

		verify(repository).move(source, target)
		verify(dao).clearSourcePath(ID)
	}

	@Test
	@Timeout(10)
	fun `a change the cloud rejects ends even if undoing it fails`() {
		val pending = entity("new", Document.MIME_TYPE_DIR).apply { status = DocumentMetadataEntity.STATUS_CREATING }
		whenever(dao.insert(any())).thenReturn(ID)
		whenever(dao.loadById(ID)).thenReturn(pending)
		doThrow(IllegalStateException("disk full")).whenever(dao).discardPendingChange(pending)
		val folder: CloudFolder = mock()
		whenever(repository.folder(root, "new")).thenReturn(folder)
		whenever(repository.create(folder)).thenThrow(NetworkConnectionException())

		val creation = inTest.create(VaultPath(vault), "new", isFolder = true)
		listings.runAll()

		assertFailsWith<NetworkConnectionException>(creation.result)
	}

	@Test
	fun `a move the cloud rejects is undone`() {
		whenever(dao.load(VAULT_ID, "a.txt")).thenReturn(entity("a.txt"), entity("a.txt"), null)
		givenMoved("a.txt", "b.txt")
		whenever(repository.move(anyOrNull<CloudFile>(), anyOrNull<CloudFile>())).thenThrow(NetworkConnectionException())
		val pausedWhileUndone = mutableListOf<Boolean>()
		doAnswer { pausedWhileUndone.add(changesPaused()) }.whenever(dao).revertMove(any(), any())

		val move = inTest.move(path("a.txt"), VaultPath(vault), "b.txt")
		listings.runAll()

		assertEquals(listOf(true), pausedWhileUndone)
		verify(dao).revertMove(ID, "application/octet-stream")
		verify(dao, never()).discardPendingChange(any())
		assertFailsWith<NetworkConnectionException>(move)
	}

	@Test
	fun `a file that has never been in the cloud is only moved in the cache`() {
		whenever(dao.load(VAULT_ID, "a.txt")).thenReturn(entity("a.txt"))
		whenever(dao.loadById(ID)).thenReturn(entity("b.txt").apply {
			sourcePath = "a.txt"
			status = DocumentMetadataEntity.STATUS_UPLOADING
		})
		val target: CloudFile = mock()
		whenever(repository.file(root, "b.txt")).thenReturn(target)

		val move = inTest.move(path("a.txt"), VaultPath(vault), "b.txt")
		listings.runAll()

		verify(repository, never()).move(anyOrNull<CloudFile>(), anyOrNull<CloudFile>())
		verify(dao).clearSourcePath(ID)
		verify(dao, never()).revertMove(any(), any())
		assertEquals(target, move.get().node)
	}

	@Test
	@Timeout(10)
	fun `a file that has never been in the cloud is moved again in the cache once its earlier move is recorded`() {
		val node = givenMovableNode("a.txt", DocumentMetadataEntity.STATUS_UPLOADING)
		whenever(repository.file(eq(root), any())).thenReturn(mock())

		val first = inTest.move(path("a.txt"), VaultPath(vault), "b.txt")
		val second = inTest.move(path("b.txt"), VaultPath(vault), "c.txt")
		listings.runAll()

		first.get()
		second.get()
		assertEquals("c.txt", node().path)
		assertNull(node().sourcePath)
		verify(dao, never()).setSourcePath(any(), any())
		verify(repository, never()).move(anyOrNull<CloudFile>(), anyOrNull<CloudFile>())
	}

	@Test
	fun `a move the cloud rejects lists both folders it touched as changed`() {
		whenever(dao.load(VAULT_ID, "a/x.txt")).thenReturn(entity("a/x.txt"))
		whenever(dao.load(VAULT_ID, "b")).thenReturn(entity("b", Document.MIME_TYPE_DIR).apply { id = ID + 1 })
		givenMoved("a/x.txt", "b/x.txt")
		whenever(repository.file(eq(root), any())).thenReturn(mock())
		whenever(repository.move(anyOrNull<CloudFile>(), anyOrNull<CloudFile>())).thenThrow(NetworkConnectionException())
		val move = inTest.move(path("a/x.txt"), path("b"), "x.txt")
		changedFolders.clear()

		listings.runAll()

		assertFailsWith<NetworkConnectionException>(move)
		assertTrue(changedFolders.containsAll(listOf(path("a"), path("b"))))
	}

	@Test
	fun `a move whose name is taken in the cloud renames the node in the cache also if its folder has been moved since`() {
		whenever(dao.load(VAULT_ID, "a")).thenReturn(entity("a", Document.MIME_TYPE_DIR))
		whenever(dao.load(VAULT_ID, "a/x.txt")).thenReturn(entity("a/x.txt"))
		// the folder a has been renamed to b in the cache before the move reached the cloud
		givenMoved("a/x.txt", "b/y.txt")
		whenever(repository.file(eq(root), any())).thenReturn(mock())
		val moved = cryptoFile("b/y.txt")
		whenever(repository.move(anyOrNull<CloudFile>(), anyOrNull<CloudFile>())).thenThrow(CloudNodeAlreadyExistsException("y.txt")).thenReturn(moved)

		inTest.move(path("a/x.txt"), path("a"), "y.txt")
		listings.runAll()

		verify(dao).renamePending(eq(VAULT_ID), eq("b/y.txt"), argThat { matches(Regex("b/y \\([0-9A-F]{5}\\)\\.txt")) }, any(), any(), isNull())
	}

	@Test
	fun `an upload whose name is taken in the cloud leaves a file that is being moved itself where it is in the cache`() {
		val uploadExecutor = QueueExecutor()
		inTest = cache(CloudTaskScheduler(direct, uploadExecutor, direct))
		givenUpload(remoteModified = null)
		val taken = writeTarget("x.txt")
		whenever(repository.write(eq(taken), any(), any(), any(), any())).thenThrow(CloudNodeAlreadyExistsException("x.txt"))
		whenever(repository.file(eq(root), argThat { matches(CONFLICT_NAME) }, any())).thenReturn(mock())
		whenever(dao.load(VAULT_ID, "b")).thenReturn(entity("b", Document.MIME_TYPE_DIR).apply { id = ID + 1 })
		upload(base = 5_000)
		// moved to b/x.txt after the upload was requested, the move waits for the upload
		inTest.move(path("a/x.txt"), path("b"), "x.txt")
		whenever(dao.loadById(ID)).thenReturn(uploading().apply {
			path = "b/x.txt"
			parentPath = "b"
			sourcePath = "a/x.txt"
		})

		uploadExecutor.runAll()

		verify(dao).setSourcePath(eq(ID), argThat { matches(Regex("a/$CONFLICT_NAME")) })
		verify(dao, never()).renamePending(any(), any(), any(), any(), any(), anyOrNull())
	}

	@Test
	fun `a move that has been undone meanwhile ends without changing anything in the cloud`() {
		whenever(dao.load(VAULT_ID, "a.txt")).thenReturn(entity("a.txt"))
		whenever(dao.loadById(ID)).thenReturn(entity("a.txt").apply { status = DocumentMetadataEntity.STATUS_UPLOADING })
		whenever(repository.file(root, "a.txt")).thenReturn(mock())
		whenever(repository.file(root, "b.txt")).thenReturn(mock())

		val move = inTest.move(path("a.txt"), VaultPath(vault), "b.txt")
		listings.runAll()

		verify(repository, never()).move(anyOrNull<CloudFile>(), anyOrNull<CloudFile>())
		verify(dao, never()).clearSourcePath(any())
		verify(dao, never()).setSourcePath(any(), any())
		verify(dao, never()).discardPendingChange(any())
		assertFailsWith<CancellationException>(move)
	}

	@Test
	fun `a move takes the node from the path it was requested for, also if the node is recorded elsewhere in the cloud`() {
		whenever(dao.load(VAULT_ID, "a.txt")).thenReturn(entity("a.txt"))
		givenMoved("a (1A2B3).txt", "b.txt")
		val requested: CloudFile = mock()
		val target: CloudFile = mock()
		whenever(repository.file(root, "a.txt")).thenReturn(requested)
		whenever(repository.file(root, "a (1A2B3).txt")).thenReturn(mock())
		whenever(repository.file(root, "b.txt")).thenReturn(target)
		val moved = cryptoFile("b.txt")
		whenever(repository.move(requested, target)).thenReturn(moved)

		val move = inTest.move(path("a.txt"), VaultPath(vault), "b.txt")
		listings.runAll()

		move.get()
		verify(repository).move(requested, target)
	}

	@Test
	fun `whether a file has been in the cloud is decided once the uploads before its move have ended`() {
		whenever(dao.load(VAULT_ID, "a.txt")).thenReturn(entity("a.txt"))
		whenever(dao.loadById(ID)).thenReturn(entity("b.txt").apply {
			sourcePath = "a.txt"
			status = DocumentMetadataEntity.STATUS_UPLOADING
		})
		val source: CloudFile = mock()
		val target: CloudFile = mock()
		whenever(repository.file(root, "a.txt")).thenReturn(source)
		whenever(repository.file(root, "b.txt")).thenReturn(target)
		val moved = cryptoFile("b.txt")
		whenever(repository.move(source, target)).thenReturn(moved)

		val move = inTest.move(path("a.txt"), VaultPath(vault), "b.txt")
		whenever(dao.loadById(ID)).thenReturn(entity("b.txt").apply {
			sourcePath = "a.txt"
			lastModified = 9_000
		})
		listings.runAll()

		move.get()
		verify(repository).move(source, target)
	}

	@Test
	fun `a file with a pending upload that has been in the cloud is moved in the cloud`() {
		whenever(dao.load(VAULT_ID, "a.txt")).thenReturn(entity("a.txt"))
		whenever(dao.loadById(ID)).thenReturn(entity("b.txt").apply {
			sourcePath = "a.txt"
			status = DocumentMetadataEntity.STATUS_UPLOADING
			lastModified = 5_000
		})
		val source: CloudFile = mock()
		val target: CloudFile = mock()
		whenever(repository.file(root, "a.txt")).thenReturn(source)
		whenever(repository.file(root, "b.txt")).thenReturn(target)
		val moved = cryptoFile("b.txt")
		whenever(repository.move(source, target)).thenReturn(moved)

		val move = inTest.move(path("a.txt"), VaultPath(vault), "b.txt")
		listings.runAll()

		move.get()
		verify(repository).move(source, target)
	}

	@Test
	fun `a move whose name is taken in the cloud reports the conflict name`() {
		whenever(dao.load(VAULT_ID, "a.txt")).thenReturn(entity("a.txt"))
		givenMoved("a.txt", "b.txt")
		val target: CloudFile = mock()
		whenever(repository.file(root, "b.txt")).thenReturn(target)
		whenever(repository.file(eq(root), argThat { matches(Regex("b \\([0-9A-F]{5}\\)\\.txt")) })).thenReturn(mock())
		whenever(repository.move(anyOrNull<CloudFile>(), eq(target))).thenThrow(CloudNodeAlreadyExistsException("b.txt"))
		val renamed = cryptoFile("renamed.txt")
		whenever(repository.move(anyOrNull<CloudFile>(), argThat<CloudFile> { this != target })).thenReturn(renamed)

		val move = inTest.move(path("a.txt"), VaultPath(vault), "b.txt")
		listings.runAll()

		assertTrue(move.get().path.path.matches(Regex("b \\([0-9A-F]{5}\\)\\.txt")))
		verify(dao).renamePending(eq(VAULT_ID), eq("b.txt"), eq(move.get().path.path), eq(move.get().path.name), any(), isNull())
	}

	@Test
	fun `deleting a folder deletes it in the cloud and returns its descendants for revoking their grants`() {
		whenever(dao.load(VAULT_ID, "a")).thenReturn(entity("a", Document.MIME_TYPE_DIR))
		whenever(dao.loadById(ID)).thenReturn(entity("a", Document.MIME_TYPE_DIR).apply { pendingDelete = 1 })
		whenever(dao.loadDescendantIds(VAULT_ID, "a")).thenReturn(listOf(8L, 9L))
		val folder: CloudFolder = mock()
		whenever(repository.folder(root, "a")).thenReturn(folder)

		val deletion = inTest.delete(path("a"))
		listings.runAll()

		assertEquals(listOf("$VAULT_ID:8", "$VAULT_ID:9"), deletion.descendantIds)
		inOrder(dao) {
			verify(dao).loadDescendantIds(VAULT_ID, "a")
			verify(dao).markPendingDelete(VAULT_ID, "a", 1)
		}
		verify(repository).delete(folder)
		verify(dao).deleteSubtree(VAULT_ID, "a")
		deletion.result.get()
	}

	@Test
	fun `a deletion the cloud rejects brings the node back without listing its folder`() {
		whenever(dao.load(VAULT_ID, "x.txt")).thenReturn(entity("x.txt"))
		whenever(dao.loadById(ID)).thenReturn(entity("x.txt").apply { pendingDelete = 1 }, entity("x.txt"))
		val file: CloudFile = mock()
		whenever(repository.file(root, "x.txt")).thenReturn(file)
		whenever(repository.delete(file)).thenThrow(NetworkConnectionException())
		val pausedWhileRestored = mutableListOf<Boolean>()
		doAnswer { pausedWhileRestored.add(changesPaused()) }.whenever(dao).clearPendingDelete(any(), any())

		val deletion = inTest.delete(path("x.txt"))
		changedFolders.clear()
		listings.runAll()

		assertEquals(listOf(true), pausedWhileRestored)
		verify(dao).clearPendingDelete(ID, 1)
		verify(dao, never()).deleteSubtree(any(), any())
		verify(dao, never()).updateLastEnumeratedAt(any(), any(), anyOrNull())
		verify(dao, never()).clearSourcePath(any())
		assertEquals(listOf(VaultPath(vault)), changedFolders)
		assertFailsWith<NetworkConnectionException>(deletion.result)
	}

	@Test
	@Timeout(10)
	fun `a deletion the cloud rejects ends even if bringing the node back fails`() {
		whenever(dao.load(VAULT_ID, "x.txt")).thenReturn(entity("x.txt"))
		whenever(dao.loadById(ID)).thenReturn(entity("x.txt").apply { pendingDelete = 1 })
		doThrow(IllegalStateException("disk full")).whenever(dao).clearPendingDelete(ID, 1)
		val file: CloudFile = mock()
		whenever(repository.file(root, "x.txt")).thenReturn(file)
		whenever(repository.delete(file)).thenThrow(NetworkConnectionException())

		val deletion = inTest.delete(path("x.txt"))
		listings.runAll()

		assertFailsWith<NetworkConnectionException>(deletion.result)
	}

	@Test
	fun `a node that comes back under the conflict name it got meanwhile has its folder listed again`() {
		whenever(dao.load(VAULT_ID, "x.txt")).thenReturn(entity("x.txt"))
		whenever(dao.loadById(ID)).thenReturn(entity("x.txt").apply { pendingDelete = 1 }, entity("x (1A2B3).txt"))
		val file: CloudFile = mock()
		whenever(repository.file(root, "x.txt")).thenReturn(file)
		whenever(repository.delete(file)).thenThrow(NetworkConnectionException())

		inTest.delete(path("x.txt"))
		changedFolders.clear()
		listings.runAll()

		verify(dao).clearPendingDelete(ID, 1)
		verify(dao).clearSourcePath(ID)
		verify(dao).updateLastEnumeratedAt(VAULT_ID, "", null)
		assertEquals(listOf(VaultPath(vault)), changedFolders)
	}

	@Test
	fun `a node that is already gone from the cloud counts as deleted`() {
		var token = 0L
		listOf(NoSuchCloudFileException("x.txt"), NotFoundException("x.txt")).forEach { gone ->
			whenever(dao.load(VAULT_ID, "x.txt")).thenReturn(entity("x.txt"))
			whenever(dao.loadById(ID)).thenReturn(entity("x.txt").apply { pendingDelete = ++token })
			val file: CloudFile = mock()
			whenever(repository.file(root, "x.txt")).thenReturn(file)
			whenever(repository.delete(file)).thenThrow(gone)

			val deletion = inTest.delete(path("x.txt"))
			listings.runAll()

			deletion.result.get()
		}
		verify(dao, times(2)).deleteSubtree(VAULT_ID, "x.txt")
		verify(dao, never()).clearPendingDelete(any(), any())
	}

	@Test
	fun `a name held by a node being deleted is given to a new node, which is created after the deletion`() {
		whenever(dao.load(VAULT_ID, "")).thenReturn(entity("", Document.MIME_TYPE_DIR).apply { lastEnumeratedAt = now })
		whenever(dao.load(VAULT_ID, "x.txt")).thenReturn(entity("x.txt"))
		whenever(dao.loadById(ID)).thenReturn(entity("x.txt").apply { pendingDelete = 1 })
		whenever(dao.insert(any())).thenReturn(ID + 1)
		val requests = mutableListOf<String>()
		whenever(repository.file(root, "x.txt")).thenReturn(mock())
		whenever(repository.delete(any<CloudFile>())).thenAnswer { requests.add("delete") }
		whenever(repository.file(eq(root), eq("x.txt"), any())).thenReturn(mock())
		whenever(repository.write(any(), any(), any(), any(), any())).thenAnswer {
			requests.add("create")
			cryptoFile("x.txt")
		}
		val deletion = inTest.delete(path("x.txt"))
		whenever(dao.load(VAULT_ID, "x.txt")).thenReturn(entity("x.txt").apply { pendingDelete = 1 })

		inTest.create(VaultPath(vault), "x.txt", isFolder = false, failIfTaken = true)
		listings.runAll()

		verify(dao).renamePending(eq(VAULT_ID), eq("x.txt"), argThat { matches(CONFLICT_NAME) }, argThat { matches(CONFLICT_NAME) }, any(), eq("x.txt"))
		verify(dao).insert(argThat { path == "x.txt" })
		assertEquals(listOf("delete", "create"), requests)
		deletion.result.get()
	}

	@Test
	fun `with failIfTaken a name taken in the cache fails the change and changes nothing`() {
		whenever(dao.load(VAULT_ID, "")).thenReturn(entity("", Document.MIME_TYPE_DIR).apply { lastEnumeratedAt = now })
		whenever(dao.load(VAULT_ID, "a.txt")).thenReturn(entity("a.txt"))
		whenever(dao.load(VAULT_ID, "b.txt")).thenReturn(entity("b.txt").apply { id = ID + 1 })
		whenever(dao.load(VAULT_ID, "folder")).thenReturn(entity("folder", Document.MIME_TYPE_DIR).apply { id = ID + 2 })

		assertThrows<CloudNodeAlreadyExistsException> { inTest.move(path("a.txt"), VaultPath(vault), "b.txt", failIfTaken = true) }
		assertThrows<CloudNodeAlreadyExistsException> { inTest.create(VaultPath(vault), "b.txt", isFolder = true, failIfTaken = true) }
		assertThrows<CloudNodeAlreadyExistsException> { inTest.createFile(path("b.txt"), replace = false) }
		assertThrows<CloudNodeIsFolderException> { inTest.createFile(path("folder"), replace = true) }
		assertThrows<CloudNodeIsFolderException> { inTest.move(path("a.txt"), VaultPath(vault), "folder", failIfTaken = true) }

		verify(dao, never()).insert(any())
		verify(dao, never()).move(any(), any(), any(), any(), any(), any())
		assertTrue(listings.isEmpty())
	}

	@Test
	fun `a change to a node that vanished meanwhile fails like for an unknown node, without listing anything`() {
		inTest = cache(CloudTaskScheduler(direct, direct, direct))
		whenever(dao.load(VAULT_ID, "a")).thenReturn(entity("a", Document.MIME_TYPE_DIR).apply { pendingDelete = 1 })

		assertThrows<FileNotFoundException> { inTest.createFile(path("a/x.txt"), replace = false) }
		assertThrows<FileNotFoundException> { inTest.create(path("a"), "new", isFolder = true) }
		assertThrows<FileNotFoundException> { inTest.move(path("b.txt"), VaultPath(vault), "c.txt") }
		assertThrows<FileNotFoundException> { inTest.delete(path("b.txt")) }

		verify(repository, never()).list(any())
		verify(dao, never()).insert(any())
	}

	@Test
	fun `a change below a file fails`() {
		whenever(dao.load(VAULT_ID, "x.txt")).thenReturn(entity("x.txt"))

		assertThrows<IllegalArgumentException> { inTest.createFile(path("x.txt/y.txt"), replace = false) }
		assertThrows<IllegalArgumentException> { inTest.create(path("x.txt"), "new", isFolder = true) }

		verify(dao, never()).insert(any())
	}

	@Test
	fun `a file is kept for replacing its content`() {
		whenever(dao.load(VAULT_ID, "b.txt")).thenReturn(entity("b.txt"))

		val target = inTest.createFile(path("b.txt"), replace = true)

		assertFalse(target.isNew)
		assertEquals(ID, target.id)
		verify(dao, never()).insert(any())
	}

	@Test
	fun `a new file waits for the upload of its local copy`() {
		whenever(dao.insert(any())).thenReturn(ID)

		val target = inTest.createFile(path("new.txt"), replace = false)

		assertTrue(target.isNew)
		assertEquals(ID, target.id)

		verify(dao).insert(argThat { path == "new.txt" && status == DocumentMetadataEntity.STATUS_UPLOADING && lastModified == null })
	}

	@Test
	fun `a change in a folder whose ancestors have never been listed lists them first`() {
		val folderA: CloudFolder = mock()
		val folderB: CloudFolder = mock()
		val folderC: CloudFolder = mock()
		whenever(repository.resolve(cryptoCloud, "/a")).thenReturn(folderA)
		whenever(repository.resolve(cryptoCloud, "/a/b")).thenReturn(folderB)
		whenever(repository.resolve(cryptoCloud, "/a/b/c")).thenReturn(folderC)
		whenever(repository.list(root)).thenAnswer {
			whenever(dao.load(VAULT_ID, "a")).thenReturn(entity("a", Document.MIME_TYPE_DIR).apply { id = ID + 1 })
			listOf(cryptoFolder("a"))
		}
		whenever(repository.list(folderA)).thenAnswer {
			whenever(dao.load(VAULT_ID, "a/b")).thenReturn(entity("a/b", Document.MIME_TYPE_DIR).apply { id = ID + 2 })
			listOf(cryptoFolder("a/b"))
		}
		whenever(repository.list(folderB)).thenAnswer {
			whenever(dao.load(VAULT_ID, "a/b/c")).thenReturn(entity("a/b/c", Document.MIME_TYPE_DIR).apply { id = ID + 3 })
			listOf(cryptoFolder("a/b/c"))
		}
		whenever(dao.insert(any())).thenReturn(ID)
		inTest = cache(CloudTaskScheduler(direct, direct, direct))

		inTest.create(path("a/b/c"), "new", isFolder = true, failIfTaken = true)

		inOrder(repository) {
			verify(repository).list(root)
			verify(repository).list(folderA)
			verify(repository).list(folderB)
		}
		verify(dao).insert(argThat { path == "a/b/c/new" })
	}

	@Test
	fun `a node the cache doesn't know lists its folder once more before it counts as unknown`() {
		whenever(dao.load(VAULT_ID, "")).thenReturn(entity("", Document.MIME_TYPE_DIR).apply { lastEnumeratedAt = now })
		whenever(repository.list(root)).thenAnswer {
			whenever(dao.load(VAULT_ID, "x.txt")).thenReturn(entity("x.txt"))
			listOf(cryptoFile("x.txt"))
		}
		inTest = cache(CloudTaskScheduler(direct, direct, direct))

		assertEquals("x.txt", inTest.requireKnownDocument(path("x.txt")).path)
		assertThrows<NoSuchCloudFileException> { inTest.requireKnownDocument(path("y.txt")) }
	}

	@Test
	fun `a cached document is used if its folder can't be listed`() {
		val cached = entity("a/x.txt")
		whenever(dao.load(VAULT_ID, "a")).thenReturn(entity("a", Document.MIME_TYPE_DIR).apply { lastEnumeratedAt = now - 60_000 })
		whenever(dao.load(VAULT_ID, "a/x.txt")).thenReturn(cached)
		whenever(repository.list(root)).thenThrow(NetworkConnectionException())
		val cache = cache(CloudTaskScheduler(direct, direct, direct))

		assertEquals(cached, cache.currentDocument(path("a/x.txt")))
	}

	@Test
	fun `a change is made in the cache if its folder can't be listed, unless a listing is required`() {
		whenever(dao.load(VAULT_ID, "")).thenReturn(entity("", Document.MIME_TYPE_DIR).apply { lastEnumeratedAt = now - 60_000 })
		whenever(repository.list(root)).thenThrow(FatalBackendException(IOException("unexpected end of stream")))
		whenever(dao.insert(any())).thenReturn(ID)
		inTest = cache(CloudTaskScheduler(direct, listings, direct))

		assertThrows<FatalBackendException> { inTest.prepareFolder(VaultPath(vault), requireListing = true) }
		inTest.create(VaultPath(vault), "new", isFolder = true, failIfTaken = true)

		verify(dao).insert(argThat { path == "new" })
	}

	@Test
	fun `a listing of the folder a move took a node from is repeated if the move finished during it`() {
		givenMovable()

		assertEquals(2, listingsWhile("a") { inTest.move(path("a/x.txt"), path("b"), "x.txt") })
	}

	@Test
	fun `a listing of the folder a move took a node to is repeated if the move finished during it`() {
		givenMovable()

		assertEquals(2, listingsWhile("b") { inTest.move(path("a/x.txt"), path("b"), "x.txt") })
	}

	@Test
	fun `a listing during which a deletion finished is repeated`() {
		whenever(dao.load(VAULT_ID, "a")).thenReturn(entity("a", Document.MIME_TYPE_DIR).apply { id = ID + 1 })
		whenever(dao.load(VAULT_ID, "a/x.txt")).thenReturn(entity("a/x.txt"))
		whenever(dao.loadById(ID)).thenReturn(entity("a/x.txt").apply { pendingDelete = 1 })

		assertEquals(2, listingsWhile("a") { inTest.delete(path("a/x.txt")) })
	}

	@Test
	fun `a listing during which a creation finished is repeated`() {
		whenever(dao.insert(any())).thenReturn(ID)
		whenever(repository.folder(root, "new")).thenReturn(mock())
		whenever(dao.load(VAULT_ID, "b")).thenReturn(entity("b", Document.MIME_TYPE_DIR))

		assertEquals(2, listingsWhile("b") { inTest.create(path("b"), "new", isFolder = true) })
		verify(dao).markCreated(ID, null, null)
	}

	@Test
	fun `a change in another folder does not repeat a listing`() {
		whenever(dao.insert(any())).thenReturn(ID)
		whenever(repository.folder(root, "new")).thenReturn(mock())
		whenever(dao.load(VAULT_ID, "b")).thenReturn(entity("b", Document.MIME_TYPE_DIR))

		assertEquals(1, listingsWhile("b") { inTest.create(VaultPath(vault), "new", isFolder = true) })
		verify(dao).markCreated(ID, null, null)
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
		whenever(repository.resolve(cryptoCloud, "/a")).thenReturn(folderA)
		whenever(repository.resolve(cryptoCloud, "/b")).thenReturn(folderB)
		whenever(dao.load(VAULT_ID, "a/x.txt")).thenReturn(entity("a/x.txt"))
		whenever(dao.load(VAULT_ID, "b")).thenReturn(entity("b", Document.MIME_TYPE_DIR).apply { id = ID + 1 })
		givenMoved("a/x.txt", "b/x.txt")
		val source: CloudFile = mock()
		val target: CloudFile = mock()
		whenever(repository.file(folderA, "x.txt")).thenReturn(source)
		whenever(repository.file(folderB, "x.txt")).thenReturn(target)

		inTest.move(path("a/x.txt"), path("b"), "x.txt")
		listings.runAll()

		verify(repository).move(source, target)
		verify(dao).clearSourcePath(ID)
	}

	@Test
	fun `changes interrupted by the end of the process are dropped when the cache is created`() {
		val interrupted = entity("new").apply { status = DocumentMetadataEntity.STATUS_CREATING }
		whenever(dao.loadInterruptedChanges()).thenReturn(listOf(interrupted))

		cache(CloudTaskScheduler(direct, direct, direct))

		verify(dao).discardPendingChange(interrupted)
	}

	@Test
	fun `deletions interrupted by the end of the process are undone before the other changes are dropped`() {
		cache(CloudTaskScheduler(direct, direct, direct))

		inOrder(dao) {
			verify(dao).recoverPendingDeletes()
			verify(dao).loadInterruptedChanges()
		}
	}

	@Test
	fun `deleting a file deletes it in the cloud`() {
		whenever(dao.load(VAULT_ID, "x.txt")).thenReturn(entity("x.txt"))
		whenever(dao.loadById(ID)).thenReturn(entity("x.txt").apply { pendingDelete = 1 })
		val file: CloudFile = mock()
		whenever(repository.file(root, "x.txt")).thenReturn(file)

		inTest.delete(path("x.txt"))
		listings.runAll()

		verify(repository).delete(file)
		verify(dao).deleteSubtree(VAULT_ID, "x.txt")
	}

	@Test
	fun `a deletion goes to the path it was requested for, also if the node is recorded elsewhere in the cloud`() {
		whenever(dao.load(VAULT_ID, "b.txt")).thenReturn(entity("b.txt"))
		// renamed aside for a new b.txt, and still moving away from a.txt
		whenever(dao.loadById(ID)).thenReturn(entity("b (1A2B3).txt").apply {
			sourcePath = "a.txt"
			pendingDelete = 1
		})
		val requested: CloudFile = mock()
		whenever(repository.file(root, "a.txt")).thenReturn(mock())
		whenever(repository.file(root, "b.txt")).thenReturn(requested)
		whenever(repository.file(root, "b (1A2B3).txt")).thenReturn(mock())

		inTest.delete(path("b.txt"))
		listings.runAll()

		verify(repository).delete(requested)
		verify(repository, times(1)).delete(any<CloudFile>())
	}

	@Test
	fun `a node that gave its name to a new node is deleted where it is in the cloud`() {
		whenever(dao.load(VAULT_ID, "x.txt")).thenReturn(entity("x.txt"))
		whenever(dao.loadById(ID)).thenReturn(entity("x (1A2B3).txt").apply {
			sourcePath = "x.txt"
			pendingDelete = 1
		})
		val file: CloudFile = mock()
		whenever(repository.file(root, "x.txt")).thenReturn(file)
		whenever(repository.file(root, "x (1A2B3).txt")).thenReturn(mock())

		inTest.delete(path("x.txt"))
		listings.runAll()

		verify(repository).delete(file)
	}

	@Test
	fun `a file that has never been in the cloud is only deleted in the cache`() {
		whenever(dao.load(VAULT_ID, "x.txt")).thenReturn(entity("x.txt"))
		whenever(dao.loadById(ID)).thenReturn(entity("x.txt").apply {
			status = DocumentMetadataEntity.STATUS_UPLOADING
			pendingDelete = 1
		})
		whenever(repository.file(root, "x.txt")).thenReturn(mock())

		val deletion = inTest.delete(path("x.txt"))
		listings.runAll()

		verify(repository, never()).delete(any<CloudFile>())
		verify(dao).deleteSubtree(VAULT_ID, "x.txt")
		deletion.result.get()
	}

	@Test
	fun `a node that gave its name to a new node records the conflict name its creation got in the cloud`() {
		whenever(dao.insert(any())).thenReturn(ID)
		whenever(dao.loadById(ID)).thenReturn(entity("new (AAAAA).txt").apply {
			status = DocumentMetadataEntity.STATUS_CREATING
			sourcePath = "new.txt"
		})
		val taken = writeTarget("new.txt", root)
		whenever(repository.write(eq(taken), any(), any(), any(), any())).thenThrow(CloudNodeAlreadyExistsException("new.txt"))
		val renamed: CloudFile = mock()
		whenever(repository.file(eq(root), argThat { matches(CONFLICT_FILE_NAME) }, any())).thenReturn(renamed)
		val createdFile = cryptoFile("new.txt")
		whenever(repository.write(eq(renamed), any(), any(), any(), any())).thenReturn(createdFile)

		inTest.create(VaultPath(vault), "new.txt", isFolder = false)
		listings.runAll()

		verify(dao, never()).renamePending(any(), any(), any(), any(), any(), anyOrNull())
		verify(dao).setSourcePath(eq(ID), argThat { matches(CONFLICT_FILE_NAME) })
	}

	@Test
	fun `abandoning an upload ends it and lists its folder again`() {
		whenever(dao.load(VAULT_ID, "a/x.txt")).thenReturn(uploading().apply { lastModified = 5_000 })

		inTest.abandonUpload(path("a/x.txt"))

		verify(dao).markSynced(ID)
		verify(dao).updateLastEnumeratedAt(VAULT_ID, "a", null)
	}

	@Test
	fun `abandoning the upload of a file that has never been in the cloud drops it`() {
		whenever(dao.load(VAULT_ID, "a/x.txt")).thenReturn(uploading())

		inTest.abandonUpload(path("a/x.txt"))

		verify(dao).deleteSubtree(VAULT_ID, "a/x.txt")
		verify(dao, never()).markSynced(any())
	}

	@Test
	fun `abandoning the upload of a file without a pending upload leaves it alone`() {
		whenever(dao.load(VAULT_ID, "a/x.txt")).thenReturn(entity("a/x.txt"))

		inTest.abandonUpload(path("a/x.txt"))

		verify(dao, never()).deleteSubtree(any(), any())
		verify(dao, never()).markSynced(any())
		verify(dao, never()).updateLastEnumeratedAt(any(), any(), anyOrNull())
	}

	@Test
	fun `uploads whose local copy was never recorded are given up`() {
		val inCloud = uploading().apply { lastModified = 5_000 }
		val neverUploaded = entity("a/new.txt").apply {
			id = ID + 1
			status = DocumentMetadataEntity.STATUS_UPLOADING
		}
		whenever(dao.loadUploadsWithoutLocalCopy()).thenReturn(listOf(inCloud, neverUploaded))

		inTest.abandonUploadsWithoutLocalCopy()

		verify(dao).markSynced(ID)
		verify(dao).deleteSubtree(VAULT_ID, "a/new.txt")
		verify(dao, times(2)).updateLastEnumeratedAt(VAULT_ID, "a", null)
	}

	@Test
	fun `documentIds resolve to the paths of their vault`() {
		val otherVaultsNode = entity("b.txt").apply {
			id = ID + 1
			vaultId = VAULT_ID + 1
		}
		val deleting = entity("c.txt").apply {
			id = ID + 2
			pendingDelete = 1
		}
		whenever(dao.loadById(ID)).thenReturn(entity("a/x.txt"))
		whenever(dao.loadById(ID + 1)).thenReturn(otherVaultsNode)
		whenever(dao.loadById(ID + 2)).thenReturn(deleting)

		assertEquals(VaultPath(vault), inTest.path("$VAULT_ID"))
		assertEquals(path("a/x.txt"), inTest.path("$VAULT_ID:$ID"))
		assertThrows<FileNotFoundException> { inTest.path("$VAULT_ID:${ID + 1}") }
		assertThrows<FileNotFoundException> { inTest.path("$VAULT_ID:${ID + 2}") }
		assertEquals(path("c.txt"), inTest.path("$VAULT_ID:${ID + 2}", includeHidden = true))
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
			val remoteFile = remoteFile(remoteModified, remoteSize)
			whenever(repository.metadata(cloudFile)).thenReturn(remoteFile)
		}
		val uploadedFile = cryptoFile("a/uploaded.txt", size = 3, modified = 9_000)
		whenever(repository.write(any(), any(), any(), any(), any())).thenReturn(uploadedFile)
	}

	/**
	 * Uploads a/x.txt based on [base], see [givenUpload], which fails before the cloud confirms it. The first [failures]
	 * writes to the cloud fail, the next one succeeds.
	 *
	 * @return the cloud file the upload writes to
	 */
	private fun failedFirstUpload(failures: Int = 1, remoteModified: Long? = 5_400, remoteSize: Long = 7, lastModified: Long? = 5_000, base: Long? = 5_000): CloudFile {
		givenUpload(remoteModified, remoteSize, lastModified)
		val target = writeTarget("x.txt")
		var failuresLeft = failures
		whenever(repository.write(any(), any(), any(), any(), any())).thenAnswer {
			it.readData()
			if (failuresLeft-- > 0) throw NetworkConnectionException() else cryptoFile("a/x.txt", size = 3, modified = 9_000)
		}
		upload(base)
		return target
	}

	/**
	 * Stops the first upload of a/x.txt, a file that has never been in the cloud, by deleting the file while it is
	 * uploaded. By then the file is at [deleteAs] in the cache. The upload's unconfirmed attempt is recorded, and the
	 * deletion waits in [listings]. With [remoteModified], the cloud holds another file of the same size at a/x.txt. With
	 * [sendIncomplete], the upload stops before it read the local copy to its end. With [changedAfterSend], the local copy
	 * is written again after the upload read it.
	 *
	 * @return the file at a/x.txt in the cloud
	 */
	private fun stoppedFirstUpload(deleteAs: String = "a/x.txt", remoteModified: Long? = null, sendIncomplete: Boolean = false, changedAfterSend: String? = null): CloudFile {
		localFile.writeText("new")
		givenUpload(remoteModified, remoteSize = localFile.length(), lastModified = null)
		writeTarget("x.txt")
		val file = repository.file(root, "x.txt")
		whenever(repository.write(any(), any(), any(), any(), any())).thenAnswer {
			val renamed = { uploading().apply { path = deleteAs; name = deleteAs.substringAfterLast('/') } }
			val visible = renamed()
			val deleting = renamed().apply { pendingDelete = 1 }
			whenever(dao.load(VAULT_ID, deleteAs)).thenReturn(visible)
			whenever(dao.loadById(ID)).thenReturn(deleting)
			// the body is sent, then the deletion stops the upload while it waits for the response
			if (!sendIncomplete) {
				it.readData()
			}
			changedAfterSend?.let { content -> localFile.writeText(content) }
			inTest.delete(path(deleteAs))
			throw org.cryptomator.domain.exception.CancellationException()
		}
		upload(base = null)
		return file
	}

	/**
	 * Reads the data source of a write to its end, like the cloud does when it encrypts and uploads it.
	 */
	private fun InvocationOnMock.readData() {
		getArgument<DataSource>(1).open(mock())?.use { it.readBytes() }
	}

	/**
	 * The metadata of a file in the cloud.
	 */
	private fun remoteFile(modified: Long, size: Long): CloudFile {
		return mock {
			on { this.modified } doReturn Date(modified)
			on { this.size } doReturn size
		}
	}

	private fun givenCloudContent(content: String) {
		whenever(repository.read(any(), any(), any())).thenAnswer { it.getArgument<OutputStream>(1).write(content.toByteArray()) }
	}

	private fun writeTarget(name: String, folder: CloudFolder = root): CloudFile {
		val target: CloudFile = mock()
		whenever(repository.file(eq(folder), eq(name), any())).thenReturn(target)
		return target
	}

	private fun upload(base: Long?): DocumentMetadataCache.Upload {
		return inTest.upload(path("a/x.txt"), localFile, { base }) { path, uploadedPath, lastModified, _ -> uploads.add(Triple(path, uploadedPath, lastModified)) }
	}

	private inline fun <reified T : Throwable> assertFailsWith(future: CompletableFuture<*>) {
		assertInstanceOf(T::class.java, assertThrows<Throwable> { future.await() })
	}

	private fun uploading(): DocumentMetadataEntity {
		return entity("a/x.txt").apply {
			status = DocumentMetadataEntity.STATUS_UPLOADING
			uploadGeneration = GENERATION
		}
	}

	/**
	 * A file moved from a/x.txt to b/x.txt with a pending upload, whose move the process ended during.
	 */
	private fun givenInterruptedMove(): DocumentMetadataEntity {
		val moved = entity("b/x.txt").apply {
			sourcePath = "a/x.txt"
			status = DocumentMetadataEntity.STATUS_UPLOADING
		}
		whenever(dao.loadInterruptedChanges()).thenReturn(listOf(moved))
		whenever(dao.hasPendingUploadInSubtree(VAULT_ID, "b/x.txt")).thenReturn(true)
		whenever(dao.loadById(ID)).thenReturn(moved)
		whenever(dao.loadPendingUploads(VAULT_ID)).thenReturn(listOf(moved))
		return moved
	}

	/**
	 * A file a/x.txt that can be moved to the folder b.
	 */
	private fun givenMovable() {
		whenever(dao.load(VAULT_ID, "a")).thenReturn(entity("a", Document.MIME_TYPE_DIR).apply { id = ID + 1 })
		whenever(dao.load(VAULT_ID, "b")).thenReturn(entity("b", Document.MIME_TYPE_DIR).apply { id = ID + 2 })
		whenever(dao.load(VAULT_ID, "a/x.txt")).thenReturn(entity("a/x.txt"))
		givenMoved("a/x.txt", "b/x.txt")
	}

	/**
	 * Lists [folder], running [change] and the cloud requests it makes while the first listing is made.
	 *
	 * @return how often the folder was listed
	 */
	private fun listingsWhile(folder: String, change: () -> Unit): Int {
		var listCalls = 0
		whenever(repository.list(root)).thenAnswer {
			if (++listCalls == 1) {
				change()
				listings.runAll()
			}
			emptyList<CloudNode>()
		}
		inTest.children(path(folder))
		listings.runAll()
		return listCalls
	}

	/**
	 * Keeps the node with [ID] where the cache moves it, like the database would.
	 */
	private fun givenMovableNode(path: String, status: String = DocumentMetadataEntity.STATUS_SYNCED): () -> DocumentMetadataEntity {
		var current = entity(path).apply { this.status = status }
		whenever(dao.load(eq(VAULT_ID), any())).thenAnswer { invocation -> current.takeIf { it.path == invocation.getArgument(1) } }
		whenever(dao.loadById(ID)).thenAnswer { current }
		doAnswer { invocation ->
			val source = current.sourcePath ?: invocation.getArgument<String>(1)
			current = entity(invocation.getArgument(2)).apply {
				this.status = status
				sourcePath = source
			}
		}.whenever(dao).move(any(), any(), any(), any(), any(), any())
		doAnswer { invocation ->
			val source = current.sourcePath ?: invocation.getArgument<String>(5)
			current = entity(invocation.getArgument(2)).apply {
				this.status = status
				sourcePath = source
			}
		}.whenever(dao).renamePending(any(), any(), any(), any(), any(), anyOrNull())
		doAnswer { current = entity(current.path).apply { this.status = status } }.whenever(dao).clearSourcePath(ID)
		doAnswer { current = entity(current.sourcePath ?: current.path).apply { this.status = status } }.whenever(dao).revertMove(eq(ID), any())
		return { current }
	}

	/**
	 * The node moved from [source] to [target] in the cache, still moving in the cloud.
	 */
	private fun givenMoved(source: String, target: String, mimeType: String = "text/plain") {
		whenever(dao.loadById(ID)).thenReturn(entity(target, mimeType).apply { sourcePath = source })
	}

	/**
	 * Whether another thread is kept from changing the cache right now.
	 */
	private fun changesPaused(): Boolean {
		val changed = CountDownLatch(1)
		Thread { inTest.withChangesPaused { changed.countDown() } }.apply { isDaemon = true }.start()
		return !changed.await(200, TimeUnit.MILLISECONDS)
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

	private fun cryptoFolder(path: String): CryptoFolder {
		return CryptoFolder(root, path.substringAfterLast('/'), "/$path", null)
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
