package org.cryptomator.presentation.presenter

import android.app.Activity
import org.cryptomator.data.cloud.crypto.CryptoCloud
import org.cryptomator.domain.CachedVaultFolder
import org.cryptomator.domain.CloudFolder
import org.cryptomator.domain.CloudType
import org.cryptomator.domain.Vault
import org.cryptomator.domain.repository.VaultListing
import org.cryptomator.domain.usecases.ResultHandler
import org.cryptomator.domain.usecases.cloud.ListVaultFolderUseCase
import org.cryptomator.domain.usecases.cloud.UploadFilesUseCase
import org.cryptomator.presentation.licensing.LicenseEnforcer
import org.cryptomator.presentation.model.CloudFolderModel
import org.cryptomator.presentation.model.VaultModel
import org.cryptomator.presentation.model.mappers.CloudFolderModelMapper
import org.cryptomator.presentation.ui.activity.view.SharedFilesView
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
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
	private val licenseEnforcer: LicenseEnforcer = mock()
	private val view: SharedFilesView = mock()
	private val activity: Activity = mock()

	init {
		val listing: ListVaultFolderUseCase.Launcher = mock()
		whenever(listVaultFolderUseCase.withFolder(any())).thenReturn(listing)
		whenever(listing.andForceRefresh(any())).thenReturn(listing)
		doAnswer { listings.add(it.getArgument(0)) }.whenever(listing).run(any())
		whenever(uploadFilesUseCase.withParent(any())).thenReturn(upload)
		whenever(upload.andFiles(any())).thenReturn(upload)
		whenever(licenseEnforcer.hasWriteAccessForVault(any())).thenReturn(true)
		whenever(view.activity()).thenReturn(activity)
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
		licenseEnforcer = licenseEnforcer,
		exceptionMappings = mock()
	).also { it.view = view }

	@Test
	fun `files are saved into the chosen vault folder where its listing found it, e g after another app renamed it`() {
		inTest.location = CloudFolderModel(CachedVaultFolder(cryptoCloud, "/E", "1:7"))

		inTest.onSaveButtonPressed(emptyList())
		listings.single().onSuccess(VaultListing(CachedVaultFolder(cryptoCloud, "/F", "1:7"), emptyList(), false, null))

		verify(uploadFilesUseCase).withParent(argThat<CloudFolder> { path == "/F" })
		assertEquals("/F", inTest.location?.path)
	}

	@Test
	fun `files are saved into a Hub vault with the paid Hub license its unlock found, which the vault list doesn't know about`() {
		whenever(licenseEnforcer.hasWriteAccessForVault(any())).thenAnswer { it.getArgument<VaultModel>(0).hasHubPaidLicense }
		val unlocked = Vault.aCopyOf(vault).withHubVault(true).withHubPaidLicense(true).build()
		val hubCloud: CryptoCloud = mock {
			on { type() } doReturn CloudType.CRYPTO
			on { vault } doReturn unlocked
		}
		inTest.onVaultSelected(VaultModel(vault))
		inTest.location = CloudFolderModel(CachedVaultFolder(hubCloud, "/E", "1:7"))

		inTest.onSaveButtonPressed(emptyList())

		assertEquals(1, listings.size)
		verify(licenseEnforcer, never()).ensureWriteAccessForVault(any(), any(), any())
	}

	@Test
	fun `without write access nothing is saved and the license screen is shown`() {
		whenever(licenseEnforcer.hasWriteAccessForVault(any())).thenReturn(false)
		inTest.onVaultSelected(VaultModel(vault))
		inTest.location = CloudFolderModel(CachedVaultFolder(cryptoCloud, "/E", "1:7"))

		inTest.onSaveButtonPressed(emptyList())

		assertEquals(0, listings.size)
		verify(licenseEnforcer).ensureWriteAccessForVault(eq(activity), argThat { vaultId == 1L }, eq(LicenseEnforcer.LockedAction.UPLOAD_FILES))
	}

	@Test
	fun `selecting another vault forgets the location chosen in the first one`() {
		val other = Vault.aCopyOf(vault).withId(2L).build()
		inTest.onVaultSelected(VaultModel(vault))
		inTest.location = CloudFolderModel(CachedVaultFolder(cryptoCloud, "/E", "1:7"))

		inTest.onVaultSelected(VaultModel(vault))
		assertEquals("/E", inTest.location?.path)
		inTest.onVaultSelected(VaultModel(other))
		assertNull(inTest.location)
	}
}
