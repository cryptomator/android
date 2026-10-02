package org.cryptomator.presentation.presenter

import android.app.Activity
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
import org.cryptomator.domain.usecases.vault.UpdateVaultParameterIfChangedRemotelyUseCase
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.licensing.LicenseEnforcer
import org.cryptomator.presentation.model.mappers.CloudFolderModelMapper
import org.cryptomator.presentation.service.ProductPrices
import org.cryptomator.presentation.service.SalePromo
import org.cryptomator.presentation.ui.activity.view.VaultListView
import org.cryptomator.presentation.ui.dialog.TrialExpiredDialog
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
import java.util.concurrent.TimeUnit

class VaultListPresenterFreemiumTest {

	private val vaultListView: VaultListView = Mockito.mock(VaultListView::class.java)
	private val activity: Activity = Mockito.mock(Activity::class.java)
	private val licenseEnforcer: LicenseEnforcer = Mockito.mock(LicenseEnforcer::class.java)
	private val sharedPreferencesHandler: SharedPreferencesHandler = Mockito.mock(SharedPreferencesHandler::class.java)
	private val exceptionMappings: ExceptionHandlers = Mockito.mock(ExceptionHandlers::class.java)

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
			Mockito.mock(UpdateVaultParameterIfChangedRemotelyUseCase::class.java),
			Mockito.mock(ListCBCEncryptedPasswordVaultsUseCase::class.java),
			Mockito.mock(RemoveStoredVaultPasswordsUseCase::class.java),
			Mockito.mock(SaveVaultsUseCase::class.java),
			Mockito.mock(NetworkConnectionCheck::class.java),
			Mockito.mock(FileUtil::class.java),
			Mockito.mock(AuthenticationExceptionHandler::class.java),
			Mockito.mock(CloudFolderModelMapper::class.java),
			licenseEnforcer,
			sharedPreferencesHandler,
			exceptionMappings
		)
		Mockito.doReturn(activity).`when`(vaultListView).activity()
		Mockito.doReturn(true).`when`(sharedPreferencesHandler).hasCompletedWelcomeFlow()
		inTest.view = vaultListView
	}

	private fun stubTrialState(active: Boolean, expired: Boolean, date: String?) {
		Mockito.doReturn(LicenseEnforcer.TrialState(active, expired, date)).`when`(licenseEnforcer).evaluateTrialState()
	}

	private fun stubHasPaidLicense(value: Boolean) {
		Mockito.doReturn(value).`when`(licenseEnforcer).hasPaidLicense()
	}

	@Test
	fun `resumed shows trial expired dialog when trial expired and no paid license`() {
		stubTrialState(active = false, expired = true, date = "Mar 28, 2026")
		stubHasPaidLicense(false)

		inTest.resumed()

		Mockito.verify(vaultListView).showDialog(isA<TrialExpiredDialog>())
	}

	@Test
	fun `resumed does not show dialog when trial expired but has paid license`() {
		stubTrialState(active = false, expired = true, date = "Mar 28, 2026")
		stubHasPaidLicense(true)

		inTest.resumed()

		Mockito.verify(vaultListView, Mockito.never()).showDialog(isA<TrialExpiredDialog>())
	}

	@Test
	fun `resumed does not show dialog when trial is active`() {
		stubTrialState(active = true, expired = false, date = "Apr 28, 2026")
		stubHasPaidLicense(false)

		inTest.resumed()

		Mockito.verify(vaultListView, Mockito.never()).showDialog(isA<TrialExpiredDialog>())
	}

	@Test
	fun `resumed does not show dialog when no trial ever started`() {
		stubTrialState(active = false, expired = false, date = null)
		stubHasPaidLicense(false)

		inTest.resumed()

		Mockito.verify(vaultListView, Mockito.never()).showDialog(isA<TrialExpiredDialog>())
	}

	@Test
	fun `resumed shows trial expired dialog only once per lifecycle`() {
		stubTrialState(active = false, expired = true, date = "Mar 28, 2026")
		stubHasPaidLicense(false)
		// In production evaluateTrialState() flips isTrialExpired to true on first observation;
		// simulate that side effect so the guard in resumed() skips the second dialog.
		Mockito.`when`(sharedPreferencesHandler.isTrialExpired()).thenReturn(false, true)

		inTest.resumed()
		inTest.resumed()

		Mockito.verify(vaultListView, Mockito.times(1)).showDialog(isA<TrialExpiredDialog>())
	}

	@Test
	fun `resumed loads product prices when no paid license`() {
		stubTrialState(active = true, expired = false, date = "Apr 28, 2026")
		stubHasPaidLicense(false)

		inTest.resumed()

		Mockito.verify(vaultListView).loadProductPrices()
	}

	@Test
	fun `resumed hides sale promo banner without loading product prices when has paid license`() {
		stubTrialState(active = false, expired = false, date = null)
		stubHasPaidLicense(true)

		inTest.resumed()

		Mockito.verify(vaultListView).hideSalePromoBanner()
		Mockito.verify(vaultListView, Mockito.never()).loadProductPrices()
	}

	private fun discountedPrices(endTimeMillis: Long): ProductPrices {
		return ProductPrices("$9.99/yr", "$49.99", "$32.99", 33, endTimeMillis)
	}

	@Test
	fun `onProductPricesLoaded shows sale promo banner when discount is running and no paid license`() {
		val saleEnd = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1)
		stubHasPaidLicense(false)

		inTest.onProductPricesLoaded(discountedPrices(saleEnd))

		Mockito.verify(vaultListView).showSalePromoBanner(SalePromo(33, saleEnd))
	}

	@Test
	fun `onProductPricesLoaded hides sale promo banner when has paid license`() {
		val saleEnd = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1)
		stubHasPaidLicense(true)

		inTest.onProductPricesLoaded(discountedPrices(saleEnd))

		Mockito.verify(vaultListView).hideSalePromoBanner()
		Mockito.verify(vaultListView, Mockito.never()).showSalePromoBanner(any())
	}

	@Test
	fun `onProductPricesLoaded hides sale promo banner when sale was dismissed`() {
		val saleEnd = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1)
		stubHasPaidLicense(false)
		Mockito.doReturn(saleEnd).`when`(sharedPreferencesHandler).salePromoDismissedUntil()

		inTest.onProductPricesLoaded(discountedPrices(saleEnd))

		Mockito.verify(vaultListView).hideSalePromoBanner()
		Mockito.verify(vaultListView, Mockito.never()).showSalePromoBanner(any())
	}

	@Test
	fun `onSalePromoBannerDismissed remembers sale end and hides banner`() {
		inTest.onSalePromoBannerDismissed(SalePromo(33, 1_700_000_000_000))

		Mockito.verify(sharedPreferencesHandler).setSalePromoDismissedUntil(1_700_000_000_000)
		Mockito.verify(vaultListView).hideSalePromoBanner()
	}
}
