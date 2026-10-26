package org.cryptomator.presentation.presenter

import android.net.Uri
import org.cryptomator.domain.usecases.DoLicenseCheckUseCase
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.service.ProductPricesCache
import org.cryptomator.presentation.ui.activity.view.LicenseView
import org.cryptomator.presentation.ui.dialog.AppIsObscuredInfoDialog
import org.cryptomator.util.SharedPreferencesHandler
import javax.inject.Inject

class LicenseCheckPresenter @Inject internal constructor(
	exceptionHandlers: ExceptionHandlers,
	doLicenseCheckUseCase: DoLicenseCheckUseCase,
	sharedPreferencesHandler: SharedPreferencesHandler,
	private val productPricesCache: ProductPricesCache
) : Presenter<LicenseView>(exceptionHandlers) {

	private val validator = LicenseKeyValidator(doLicenseCheckUseCase, sharedPreferencesHandler, { view }, ::showError)

	override fun resumed() {
		loadProductPrices(productPricesCache)
	}

	fun validate(data: Uri?) = validator.validate(data)
	fun validateDialogAware(license: String?) = validator.validateDialogAware(license)
	fun onFilteredTouchEventForSecurity() = view?.showDialog(AppIsObscuredInfoDialog.newInstance())

	init { unsubscribeOnDestroy(doLicenseCheckUseCase) }
}
