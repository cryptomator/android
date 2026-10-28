package org.cryptomator.presentation.presenter

import android.app.Activity
import org.cryptomator.data.cloud.crypto.CryptoCloud
import org.cryptomator.domain.CachedVaultFolder
import org.cryptomator.domain.CloudNode
import org.cryptomator.domain.CloudType
import org.cryptomator.domain.Vault
import org.cryptomator.domain.VaultPath
import org.cryptomator.domain.repository.VaultDocumentListener
import org.cryptomator.domain.repository.VaultDocumentRepository
import org.cryptomator.domain.repository.VaultListing
import org.cryptomator.domain.usecases.ResultHandler
import org.cryptomator.domain.usecases.cloud.ListVaultFolderUseCase
import org.cryptomator.domain.usecases.vault.AssertUnlockedUseCase
import org.cryptomator.presentation.model.CloudFolderModel
import org.cryptomator.presentation.model.mappers.CloudFolderModelMapper
import org.cryptomator.presentation.ui.activity.view.BrowseFilesView
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

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
	}

	private val inTest = BrowseFilesPresenter(
		getCloudListUseCase = mock(),
		createFolderUseCase = mock(),
		downloadFilesUseCase = mock(),
		deleteNodesUseCase = mock(),
		uploadFilesUseCase = mock(),
		renameFileUseCase = mock(),
		renameFolderUseCase = mock(),
		assertUnlockedUseCase = assertUnlockedUseCase,
		fileUtil = mock(),
		fileNameBlacklist = mock(),
		folderNameBlacklist = mock(),
		moveFilesUseCase = mock(),
		moveFoldersUseCase = mock(),
		getCloudListRecursiveUseCase = mock(),
		getDecryptedCloudForVaultUseCase = mock(),
		prepareDownloadFilesUseCase = mock(),
		resumeVaultUploadsUseCase = mock(),
		openVaultFilesUseCase = mock(),
		startExternalEditingUseCase = mock(),
		endExternalEditingUseCase = mock(),
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
		progressStateModelMapper = mock(),
		progressModelMapper = mock(),
		shareFileHelper = mock(),
		sharedPreferencesHandler = mock(),
		licenseEnforcer = mock(),
		exceptionMappings = mock()
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
}
