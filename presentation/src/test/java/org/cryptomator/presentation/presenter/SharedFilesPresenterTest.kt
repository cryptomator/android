package org.cryptomator.presentation.presenter

import org.cryptomator.data.cloud.crypto.CryptoCloud
import org.cryptomator.domain.CachedVaultFolder
import org.cryptomator.domain.CloudFolder
import org.cryptomator.domain.CloudType
import org.cryptomator.domain.Vault
import org.cryptomator.domain.repository.VaultListing
import org.cryptomator.domain.usecases.ResultHandler
import org.cryptomator.domain.usecases.cloud.ListVaultFolderUseCase
import org.cryptomator.domain.usecases.cloud.UploadFilesUseCase
import org.cryptomator.presentation.model.CloudFolderModel
import org.cryptomator.presentation.model.mappers.CloudFolderModelMapper
import org.cryptomator.presentation.ui.activity.view.SharedFilesView
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SharedFilesPresenterTest {

	private val vault = Vault.aVault().withId(1L).withName("vault").withPath("/vault").withCloudType(CloudType.LOCAL).withPosition(0).withUnlocked(true).build()
	private val cryptoCloud: CryptoCloud = mock {
		on { type() } doReturn CloudType.CRYPTO
		on { vault } doReturn vault
	}
	private val listVaultFolderUseCase: ListVaultFolderUseCase = mock()
	private val listings = mutableListOf<ResultHandler<VaultListing>>()
	private val uploadFilesUseCase: UploadFilesUseCase = mock()
	private val upload: UploadFilesUseCase.Launcher = mock()

	init {
		val listing: ListVaultFolderUseCase.Launcher = mock()
		whenever(listVaultFolderUseCase.withFolder(any())).thenReturn(listing)
		whenever(listing.andForceRefresh(any())).thenReturn(listing)
		doAnswer { listings.add(it.getArgument(0)) }.whenever(listing).run(any())
		whenever(uploadFilesUseCase.withParent(any())).thenReturn(upload)
		whenever(upload.andFiles(any())).thenReturn(upload)
	}

	private val inTest = SharedFilesPresenter(
		getVaultListUseCase = mock(),
		getRootFolderUseCase = mock(),
		getDecryptedCloudForVaultUseCase = mock(),
		uploadFilesUseCase = uploadFilesUseCase,
		getCloudListUseCase = mock(),
		listVaultFolderUseCase = listVaultFolderUseCase,
		contentResolverUtil = mock(),
		fileCacheUtils = mock(),
		authenticationExceptionHandler = mock(),
		cloudFolderModelMapper = CloudFolderModelMapper(),
		progressModelMapper = mock(),
		licenseEnforcer = mock(),
		exceptionMappings = mock()
	).also { it.view = mock<SharedFilesView>() }

	@Test
	fun `files are saved into the chosen vault folder where its listing found it, e g after another app renamed it`() {
		inTest.location = CloudFolderModel(CachedVaultFolder(cryptoCloud, "/E", "1:7"))

		inTest.onSaveButtonPressed(emptyList())
		listings.single().onSuccess(VaultListing(CachedVaultFolder(cryptoCloud, "/F", "1:7"), emptyList(), false, null))

		verify(uploadFilesUseCase).withParent(argThat<CloudFolder> { path == "/F" })
		assertEquals("/F", inTest.location?.path)
	}
}
