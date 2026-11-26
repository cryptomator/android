package org.cryptomator.presentation.presenter

import android.app.Activity
import android.content.res.Resources
import android.net.Uri
import android.os.StrictMode
import android.provider.DocumentsContract
import android.widget.Toast
import org.cryptomator.data.cloud.crypto.CryptoCloud
import org.cryptomator.domain.CachedVaultFolder
import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.CloudNode
import org.cryptomator.domain.CloudType
import org.cryptomator.domain.Vault
import org.cryptomator.domain.VaultPath
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.domain.repository.VaultDocumentListener
import org.cryptomator.domain.repository.VaultDocumentRepository
import org.cryptomator.domain.repository.VaultListing
import org.cryptomator.domain.usecases.ProgressAwareResultHandler
import org.cryptomator.domain.usecases.ResultHandler
import org.cryptomator.domain.usecases.cloud.DownloadState
import org.cryptomator.domain.usecases.cloud.EndExternalEditingUseCase
import org.cryptomator.domain.usecases.cloud.ImportMicrosoftWorkaroundCopyUseCase
import org.cryptomator.domain.usecases.cloud.ListVaultFolderUseCase
import org.cryptomator.domain.usecases.cloud.OpenVaultFilesUseCase
import org.cryptomator.domain.usecases.cloud.PrepareMicrosoftWorkaroundCopyUseCase
import org.cryptomator.domain.usecases.cloud.StartExternalEditingUseCase
import org.cryptomator.domain.usecases.vault.AssertUnlockedUseCase
import org.cryptomator.presentation.R
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.licensing.LicenseEnforcer
import org.cryptomator.presentation.model.CloudFileModel
import org.cryptomator.presentation.model.CloudFolderModel
import org.cryptomator.presentation.model.ProgressStateModel
import org.cryptomator.presentation.model.mappers.CloudFolderModelMapper
import org.cryptomator.presentation.model.mappers.ProgressStateModelMapper
import org.cryptomator.presentation.service.OpenWritableFileNotification
import org.cryptomator.presentation.ui.activity.view.BrowseFilesView
import org.cryptomator.presentation.util.ExternalEditingSteps
import org.cryptomator.presentation.util.FileUtil
import org.cryptomator.util.SharedPreferencesHandler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

class BrowseFilesPresenterTest {

	private val vault = Vault.aVault().withId(1L).withName("vault").withPath("/vault").withCloudType(CloudType.LOCAL).withPosition(0).withUnlocked(true).build()
	private val cryptoCloud: CryptoCloud = mock {
		on { type() } doReturn CloudType.CRYPTO
		on { vault } doReturn vault
	}
	private val activity: Activity = mock()
	private var displayed = CloudFolderModel(CachedVaultFolder(cryptoCloud, "/a/E", "1:7"))
	private val view: BrowseFilesView = mock()
	private val vaultDocumentRepository: VaultDocumentRepository = mock()
	private val listVaultFolderUseCase: ListVaultFolderUseCase = mock()
	private val listings = mutableListOf<ResultHandler<VaultListing>>()
	private val assertUnlockedUseCase: AssertUnlockedUseCase = mock()
	private val endExternalEditingUseCase: EndExternalEditingUseCase = mock()
	private val unpin: EndExternalEditingUseCase.Launcher = mock()
	private val prepareMicrosoftWorkaroundCopyUseCase: PrepareMicrosoftWorkaroundCopyUseCase = mock()
	private val prepare: PrepareMicrosoftWorkaroundCopyUseCase.Launcher = mock()
	private val importMicrosoftWorkaroundCopyUseCase: ImportMicrosoftWorkaroundCopyUseCase = mock()
	private val import: ImportMicrosoftWorkaroundCopyUseCase.Launcher = mock()
	private val externalEditingSteps = ExternalEditingSteps()
	private val document: Uri = mock()
	private val openVaultFilesUseCase: OpenVaultFilesUseCase = mock()
	private val openVaultFiles: OpenVaultFilesUseCase.Launcher = mock()
	private val startExternalEditingUseCase: StartExternalEditingUseCase = mock()
	private val progressStateModelMapper: ProgressStateModelMapper = mock()
	private val exceptionMappings: ExceptionHandlers = mock()
	private val fileUtil: FileUtil = mock()
	private val sharedPreferencesHandler: SharedPreferencesHandler = mock()
	private val licenseEnforcer: LicenseEnforcer = mock()

	@TempDir
	lateinit var directory: File

	init {
		doAnswer { it.getArgument<Runnable>(0).run() }.whenever(activity).runOnUiThread(any())
		whenever(view.activity()).thenReturn(activity)
		whenever(view.folder).thenAnswer { displayed }
		doAnswer { displayed = it.getArgument(0) }.whenever(view).updateActiveFolder(any())
		whenever(vaultDocumentRepository.pathOf(any())).thenAnswer { VaultPath(vault, it.getArgument<CloudNode>(0).path) }
		val launcher: ListVaultFolderUseCase.Launcher = mock()
		whenever(listVaultFolderUseCase.withFolder(any())).thenReturn(launcher)
		whenever(launcher.andForceRefresh(any())).thenReturn(launcher)
		doAnswer { listings.add(it.getArgument(0)) }.whenever(launcher).run(any())
		val assertion: AssertUnlockedUseCase.Launcher = mock()
		whenever(assertUnlockedUseCase.withVault(any())).thenReturn(assertion)
		whenever(endExternalEditingUseCase.withDocumentId(any())).thenReturn(unpin)
		whenever(prepareMicrosoftWorkaroundCopyUseCase.withDocument(any())).thenReturn(prepare)
		whenever(prepare.andTarget(any())).thenReturn(prepare)
		whenever(importMicrosoftWorkaroundCopyUseCase.withCopy(any())).thenReturn(import)
		whenever(import.andHashWhenOpened(any())).thenReturn(import)
		whenever(import.andDocument(any())).thenReturn(import)
		whenever(openVaultFilesUseCase.withFiles(any())).thenReturn(openVaultFiles)
		whenever(progressStateModelMapper.toModel(any<DownloadState>())).thenReturn(ProgressStateModel.DELETION)
		whenever(view.context()).thenReturn(activity)
	}

	private val inTest = browser()

	private fun browser() = BrowseFilesPresenter(
		getCloudListUseCase = mock(),
		createFolderUseCase = mock(),
		downloadFilesUseCase = mock(),
		deleteNodesUseCase = mock(),
		uploadFilesUseCase = mock(),
		renameFileUseCase = mock(),
		renameFolderUseCase = mock(),
		assertUnlockedUseCase = assertUnlockedUseCase,
		fileUtil = fileUtil,
		fileNameBlacklist = mock(),
		folderNameBlacklist = mock(),
		moveFilesUseCase = mock(),
		moveFoldersUseCase = mock(),
		getCloudListRecursiveUseCase = mock(),
		getDecryptedCloudForVaultUseCase = mock(),
		prepareDownloadFilesUseCase = mock(),
		resumeVaultUploadsUseCase = mock(),
		openVaultFilesUseCase = openVaultFilesUseCase,
		startExternalEditingUseCase = startExternalEditingUseCase,
		endExternalEditingUseCase = endExternalEditingUseCase,
		prepareMicrosoftWorkaroundCopyUseCase = prepareMicrosoftWorkaroundCopyUseCase,
		importMicrosoftWorkaroundCopyUseCase = importMicrosoftWorkaroundCopyUseCase,
		externalEditingSteps = externalEditingSteps,
		listVaultFolderUseCase = listVaultFolderUseCase,
		ensureVaultThumbnailsUseCase = mock(),
		vaultDocumentRepository = vaultDocumentRepository,
		contentResolverUtil = mock(),
		addExistingVaultWorkflow = mock(),
		createNewVaultWorkflow = mock(),
		fileCacheUtils = mock(),
		authenticationExceptionHandler = mock(),
		cloudNodeModelMapper = mock(),
		cloudFileModelMapper = mock(),
		cloudFolderModelMapper = CloudFolderModelMapper(),
		mimeTypes = mock(),
		progressStateModelMapper = progressStateModelMapper,
		progressModelMapper = mock(),
		shareFileHelper = mock(),
		sharedPreferencesHandler = sharedPreferencesHandler,
		licenseEnforcer = licenseEnforcer,
		exceptionMappings = exceptionMappings
	).also { it.view = view }

	@Test
	fun `the displayed folder is shown where its listing found it, e g after another app renamed it`() {
		inTest.resume()

		listings.single().onSuccess(VaultListing(CachedVaultFolder(cryptoCloud, "/a/E (1A2B3)", "1:7"), emptyList(), false, null))

		verify(view).updateActiveFolder(argThat { path == "/a/E (1A2B3)" })
		inOrder(view) {
			verify(view).updateTitle(argThat { path == "/a/E (1A2B3)" })
			// last, since in selection mode showing the nodes sets the selection title again
			verify(view).showCloudNodes(any())
		}
		assertEquals("1:7", (displayed.toCloudNode() as CachedVaultFolder).documentId)
	}

	@Test
	fun `the displayed folder stays as it is if its listing found it there`() {
		inTest.resume()

		listings.single().onSuccess(VaultListing(CachedVaultFolder(cryptoCloud, "/a/E", "1:7"), emptyList(), false, null))

		verify(view).showCloudNodes(any())
		verify(view, never()).updateActiveFolder(any())
	}

	@Test
	fun `a change above the displayed folder lists it again, since it may have moved it, a change elsewhere doesn't`() {
		inTest.resume()
		val listener = argumentCaptor<VaultDocumentListener>().apply { verify(vaultDocumentRepository).addListener(capture()) }.firstValue
		listings.single().onFinished()

		listener.onChanged(VaultPath(vault, "b"))
		listener.onChanged(VaultPath(vault, "a/E/c"))
		assertEquals(1, listings.size)

		listener.onChanged(VaultPath(vault, "a"))
		assertEquals(2, listings.size)
	}

	@Test
	fun `returning from a Microsoft workaround copy ends the session and hands the copy over to be saved, with the base pinned until then`() {
		val copy = microsoftWorkaroundCopy()
		val hash = byteArrayOf(1, 2, 3)
		inTest.uriToOpenedFile = document
		inTest.microsoftWorkaroundSession = MicrosoftWorkaroundSession(copy, hash)

		openFileFinished()

		inOrder(importMicrosoftWorkaroundCopyUseCase, import) {
			verify(importMicrosoftWorkaroundCopyUseCase).withCopy(copy)
			verify(import).andHashWhenOpened(hash)
			verify(import).andDocument(document)
			verify(import).run(any())
		}
		// the changes are only in the copy until it is saved
		assertTrue(copy.exists())
		assertNull(inTest.uriToOpenedFile)
		assertNull(inTest.microsoftWorkaroundSession)
		// the save is based on the pinned version
		verify(endExternalEditingUseCase, never()).withDocumentId(any())

		withDocumentIds { argumentCaptor<ResultHandler<Void?>>().apply { verify(import).run(capture()) }.firstValue.onFinished() }

		verify(endExternalEditingUseCase).withDocumentId("1:4")
		verify(unpin).run(any())
	}

	@Test
	fun `returning from a document the editor saved through the provider only ends the session`() {
		inTest.uriToOpenedFile = document

		withDocumentIds { openFileFinished() }

		verify(endExternalEditingUseCase).withDocumentId("1:4")
		verify(unpin).run(any())
		verify(importMicrosoftWorkaroundCopyUseCase, never()).withCopy(any())
		assertNull(inTest.uriToOpenedFile)
	}

	@Test
	fun `closing the browser while a Microsoft workaround copy is edited deletes the copy, which nothing would save, and unpins the base`() {
		val copy = microsoftWorkaroundCopy()
		inTest.uriToOpenedFile = document
		inTest.microsoftWorkaroundSession = MicrosoftWorkaroundSession(copy, byteArrayOf(1))
		whenever(activity.isFinishing).thenReturn(true)

		withDocumentIds { Mockito.mockConstruction(OpenWritableFileNotification::class.java).use { inTest.destroy() } }

		assertFalse(copy.parentFile!!.exists())
		assertNull(inTest.uriToOpenedFile)
		assertNull(inTest.microsoftWorkaroundSession)
		verify(endExternalEditingUseCase).withDocumentId("1:4")
		verify(importMicrosoftWorkaroundCopyUseCase, never()).withCopy(any())
	}

	@Test
	fun `leaving the browser doesn't stop a Microsoft workaround copy from being made or saved, so that it is cleaned up or kept`() {
		inTest.destroy()

		verify(prepareMicrosoftWorkaroundCopyUseCase, never()).unsubscribe()
		verify(importMicrosoftWorkaroundCopyUseCase, never()).unsubscribe()
	}

	@Test
	fun `a copy that couldn't be saved is reported as an error, not as gone`() {
		val failure = FatalBackendException(IOException("No space left on device"))
		val handler = returnFromMicrosoftWorkaroundCopy()

		Mockito.mockStatic(Toast::class.java).use { toasts ->
			handler.onError(failure)

			toasts.verifyNoInteractions()
		}
		verify(exceptionMappings).handle(view, failure)
	}

	@Test
	fun `a copy that is gone when the editor returns is reported as such`() {
		val handler = returnFromMicrosoftWorkaroundCopy()
		val toast: Toast = mock()

		Mockito.mockStatic(Toast::class.java).use { toasts ->
			toasts.`when`<Toast> { Toast.makeText(anyOrNull(), anyInt(), anyInt()) }.thenReturn(toast)

			handler.onError(FileNotFoundException("Copy copy-folder not found"))

			toasts.verify { Toast.makeText(anyOrNull(), eq(R.string.error_file_not_found_after_opening_using_3party), anyInt()) }
		}
		verify(toast).show()
		verify(exceptionMappings, never()).handle(any(), any())
	}

	@Test
	fun `a file isn't opened in an editor while a Microsoft workaround copy is still being saved, which a new copy would miss`() {
		val startLauncher: StartExternalEditingUseCase.Launcher = mock()
		whenever(startExternalEditingUseCase.withDocumentId(any())).thenReturn(startLauncher)
		val saving = returnFromMicrosoftWorkaroundCopy()

		openInEditor("Budget.xlsx")

		verify(startExternalEditingUseCase, never()).withDocumentId(any())

		withDocumentIds { saving.onFinished() }
		openInEditor("Budget.xlsx")

		verify(startExternalEditingUseCase).withDocumentId("1:4")
	}

	@Test
	fun `a browser that was recreated while a Microsoft workaround copy is still being saved doesn't open a file in an editor either`() {
		val startLauncher: StartExternalEditingUseCase.Launcher = mock()
		whenever(startExternalEditingUseCase.withDocumentId(any())).thenReturn(startLauncher)
		val saving = returnFromMicrosoftWorkaroundCopy()
		val recreated = browser()

		openInEditor("Budget.xlsx", recreated)

		verify(startExternalEditingUseCase, never()).withDocumentId(any())

		withDocumentIds { saving.onFinished() }
		openInEditor("Budget.xlsx", recreated)

		verify(startExternalEditingUseCase).withDocumentId("1:4")
	}

	@Test
	fun `a file can be opened in an editor again after pinning the base of another one failed`() {
		val pinning = startOpeningInEditor()

		withDocumentIds { fail(pinning) }
		openInEditor("Budget.xlsx")

		verify(startExternalEditingUseCase, times(2)).withDocumentId("1:4")
	}

	@Test
	fun `a file can be opened in an editor again after the browser was left while the base of another one was pinned`() {
		val pinning = startOpeningInEditor()
		inTest.pause()

		withDocumentIds { succeed(pinning, null) }
		openInEditor("Budget.xlsx")

		verify(unpin).run(any())
		verify(startExternalEditingUseCase, times(2)).withDocumentId("1:4")
	}

	@Test
	fun `a browser that was recreated while the base of a file is pinned doesn't open a file in an editor, whose base the first one would unpin`() {
		val pinning = startOpeningInEditor()
		inTest.pause()
		val recreated = browser()

		openInEditor("Budget.xlsx", recreated)

		verify(startExternalEditingUseCase).withDocumentId("1:4")

		withDocumentIds { succeed(pinning, null) }
		openInEditor("Budget.xlsx", recreated)

		inOrder(unpin, startExternalEditingUseCase) {
			verify(unpin).run(any())
			verify(startExternalEditingUseCase).withDocumentId("1:4")
		}
	}

	@Test
	fun `a browser that was recreated while a Microsoft workaround copy is made doesn't open a file in an editor either`() {
		val copy = microsoftWorkaroundCopy()
		useMicrosoftWorkaround(copy)
		val pinning = startOpeningInEditor()
		withDocumentIds { succeed(pinning, null) }
		val preparing = argumentCaptor<ResultHandler<ByteArray>>().apply { verify(prepare).run(capture()) }.firstValue
		inTest.pause()
		val recreated = browser()

		openInEditor("Budget.xlsx", recreated)

		verify(startExternalEditingUseCase).withDocumentId("1:4")

		withFileUris { withDocumentIds { succeed(preparing, byteArrayOf(1)) } }
		openInEditor("Budget.xlsx", recreated)

		// the browser that was left neither launches the editor nor keeps the copy
		assertFalse(copy.parentFile!!.exists())
		inOrder(unpin, startExternalEditingUseCase) {
			verify(unpin).run(any())
			verify(startExternalEditingUseCase).withDocumentId("1:4")
		}
	}

	private fun useMicrosoftWorkaround(copy: File) {
		val resources: Resources = mock {
			on { getStringArray(R.array.microsoft_extensions) } doReturn arrayOf("xlsx")
		}
		whenever(activity.resources).thenReturn(resources)
		whenever(licenseEnforcer.hasWriteAccessForVault(anyOrNull())).thenReturn(true)
		whenever(sharedPreferencesHandler.microsoftWorkaround()).thenReturn(true)
		whenever(fileUtil.microsoftWorkaroundCopyFor(any())).thenReturn(copy)
	}

	/**
	 * Lets a Microsoft workaround copy be handed over as a file URI.
	 */
	private fun withFileUris(block: () -> Unit) {
		Mockito.mockStatic(StrictMode::class.java).use {
			Mockito.mockConstruction(StrictMode.VmPolicy.Builder::class.java).use {
				Mockito.mockStatic(Uri::class.java).use { uris ->
					uris.`when`<Uri> { Uri.fromFile(any()) }.thenReturn(mock())
					block()
				}
			}
		}
	}

	/**
	 * As a use case reports its result.
	 */
	private fun <T> succeed(handler: ResultHandler<T>, result: T) {
		handler.onSuccess(result)
		handler.onFinished()
	}

	private fun fail(handler: ResultHandler<*>) {
		handler.onError(RuntimeException())
		handler.onFinished()
	}

	/**
	 * Clicks a file and, while its base is being pinned, another one, which isn't opened.
	 *
	 * @return the handler of the step that pins the base of the first file
	 */
	private fun startOpeningInEditor(): ResultHandler<Void?> {
		val startLauncher: StartExternalEditingUseCase.Launcher = mock()
		whenever(startExternalEditingUseCase.withDocumentId(any())).thenReturn(startLauncher)
		openInEditor("Budget.xlsx")
		openInEditor("Budget.xlsx")
		verify(startExternalEditingUseCase).withDocumentId("1:4")
		return argumentCaptor<ResultHandler<Void?>>().apply { verify(startLauncher).run(capture()) }.firstValue
	}

	/**
	 * @return the handler of the save that the return started
	 */
	private fun returnFromMicrosoftWorkaroundCopy(): ResultHandler<Void?> {
		inTest.uriToOpenedFile = document
		inTest.microsoftWorkaroundSession = MicrosoftWorkaroundSession(microsoftWorkaroundCopy(), byteArrayOf(1))
		openFileFinished()
		return argumentCaptor<ResultHandler<Void?>>().apply { verify(import).run(capture()) }.firstValue
	}

	/**
	 * Clicks the file called [name] in [browser] and lets its download finish.
	 */
	private fun openInEditor(name: String, browser: BrowseFilesPresenter = inTest) {
		val file: CloudFileModel = mock {
			on { this.name } doReturn name
			on { toCloudNode() } doReturn mock<CloudFile>()
		}
		val downloads = Mockito.mockingDetails(openVaultFiles).invocations.size
		browser.onFileClicked(file)
		val download = argumentCaptor<ProgressAwareResultHandler<List<String>, DownloadState>>().apply { verify(openVaultFiles, Mockito.atLeastOnce()).run(capture()) }.lastValue
		assertEquals(downloads + 1, Mockito.mockingDetails(openVaultFiles).invocations.size)
		withDocumentIds { download.onSuccess(listOf("1:4")) }
	}

	private fun microsoftWorkaroundCopy(): File {
		return File(directory, "copy-folder/Budget.xlsx").apply { parentFile!!.mkdirs() }.apply { writeText("saved by the editor") }
	}

	private fun openFileFinished() {
		Mockito.mockConstruction(OpenWritableFileNotification::class.java).use {
			inTest.openFileFinished(mock())
		}
	}

	private fun withDocumentIds(block: () -> Unit) {
		Mockito.mockStatic(DocumentsContract::class.java).use { documents ->
			documents.`when`<String> { DocumentsContract.getDocumentId(document) }.thenReturn("1:4")
			documents.`when`<Uri> { DocumentsContract.buildDocumentUri(any(), eq("1:4")) }.thenReturn(document)
			block()
		}
	}
}
