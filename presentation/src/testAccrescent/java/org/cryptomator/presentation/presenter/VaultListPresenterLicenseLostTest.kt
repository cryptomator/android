package org.cryptomator.presentation.presenter

import android.app.Activity
import android.os.Bundle
import org.cryptomator.data.util.NetworkConnectionCheck
import org.cryptomator.domain.usecases.DoUpdateCheckUseCase
import org.cryptomator.domain.usecases.DoUpdateUseCase
import org.cryptomator.domain.usecases.GetDecryptedCloudForVaultUseCase
import org.cryptomator.domain.usecases.cloud.GetRootFolderUseCase
import org.cryptomator.domain.usecases.vault.DeleteVaultUseCase
import org.cryptomator.domain.usecases.vault.GetVaultListUseCase
import org.cryptomator.domain.usecases.vault.ListCBCEncryptedPasswordVaultsUseCase
import org.cryptomator.domain.usecases.vault.LockVaultUseCase
import org.cryptomator.domain.usecases.vault.MoveVaultPositionUseCase
import org.cryptomator.domain.usecases.vault.RemoveStoredVaultPasswordsUseCase
import org.cryptomator.domain.usecases.vault.RenameVaultUseCase
import org.cryptomator.domain.usecases.vault.SaveVaultUseCase
import org.cryptomator.domain.usecases.vault.SaveVaultsUseCase
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.licensing.LicenseEnforcer
import org.cryptomator.presentation.model.mappers.CloudFolderModelMapper
import org.cryptomator.presentation.service.ProductPricesCache
import org.cryptomator.presentation.ui.activity.view.VaultListView
import org.cryptomator.presentation.ui.dialog.LicenseLostDialog
import org.cryptomator.presentation.util.FileUtil
import org.cryptomator.presentation.workflow.AddExistingVaultWorkflow
import org.cryptomator.presentation.workflow.AuthenticationExceptionHandler
import org.cryptomator.presentation.workflow.CreateNewVaultWorkflow
import org.cryptomator.util.SharedPreferencesHandler
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.isA

class VaultListPresenterLicenseLostTest {

	private val vaultListView: VaultListView = Mockito.mock(VaultListView::class.java)
	private val sharedPreferencesHandler: SharedPreferencesHandler = Mockito.mock(SharedPreferencesHandler::class.java)

	private lateinit var inTest: VaultListPresenter

	@BeforeEach
	fun setup() {
		inTest = VaultListPresenter(
			Mockito.mock(GetVaultListUseCase::class.java),
			Mockito.mock(DeleteVaultUseCase::class.java),
			Mockito.mock(RenameVaultUseCase::class.java),
			Mockito.mock(LockVaultUseCase::class.java),
			Mockito.mock(GetDecryptedCloudForVaultUseCase::class.java),
			Mockito.mock(GetRootFolderUseCase::class.java),
			Mockito.mock(AddExistingVaultWorkflow::class.java),
			Mockito.mock(CreateNewVaultWorkflow::class.java),
			Mockito.mock(SaveVaultUseCase::class.java),
			Mockito.mock(MoveVaultPositionUseCase::class.java),
			Mockito.mock(DoUpdateCheckUseCase::class.java),
			Mockito.mock(DoUpdateUseCase::class.java),
			Mockito.mock(ListCBCEncryptedPasswordVaultsUseCase::class.java),
			Mockito.mock(RemoveStoredVaultPasswordsUseCase::class.java),
			Mockito.mock(SaveVaultsUseCase::class.java),
			Mockito.mock(NetworkConnectionCheck::class.java),
			Mockito.mock(FileUtil::class.java),
			Mockito.mock(AuthenticationExceptionHandler::class.java),
			Mockito.mock(CloudFolderModelMapper::class.java),
			Mockito.mock(LicenseEnforcer::class.java),
			sharedPreferencesHandler,
			Mockito.mock(ProductPricesCache::class.java),
			Mockito.mock(ExceptionHandlers::class.java)
		)
		Mockito.doReturn(Mockito.mock(Activity::class.java)).`when`(vaultListView).activity()
		Mockito.doReturn(true).`when`(sharedPreferencesHandler).hasCompletedWelcomeFlow()
		inTest.view = vaultListView
	}

	private fun stubLicense(licenseToken: String, mail: String) {
		Mockito.doReturn(licenseToken).`when`(sharedPreferencesHandler).licenseToken()
		Mockito.doReturn(mail).`when`(sharedPreferencesHandler).mail()
	}

	@Test
	fun `resumed shows license lost dialog and clears mail when token was lost`() {
		stubLicense(licenseToken = "", mail = "user@example.com")

		Mockito.mockConstruction(Bundle::class.java).use { bundles ->
			inTest.resumed()

			Mockito.verify(sharedPreferencesHandler).setMail("")
			Mockito.verify(vaultListView).showDialog(isA<LicenseLostDialog>())
			Mockito.verify(bundles.constructed().single()).putString("mail", "user@example.com")
		}
	}

	@Test
	fun `resumed does not show license lost dialog when license token exists`() {
		stubLicense(licenseToken = "token", mail = "user@example.com")

		inTest.resumed()

		Mockito.verify(sharedPreferencesHandler, Mockito.never()).setMail(any())
		Mockito.verify(vaultListView, Mockito.never()).showDialog(any())
	}

	@Test
	fun `resumed does not show license lost dialog when no license was ever entered`() {
		stubLicense(licenseToken = "", mail = "")

		inTest.resumed()

		Mockito.verify(sharedPreferencesHandler, Mockito.never()).setMail(any())
		Mockito.verify(vaultListView, Mockito.never()).showDialog(any())
	}
}
