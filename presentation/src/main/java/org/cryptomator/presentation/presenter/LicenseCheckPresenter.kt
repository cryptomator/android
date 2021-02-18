package org.cryptomator.presentation.presenter

import android.net.Uri
import org.cryptomator.domain.usecases.DoLicenseCheckUseCase
import org.cryptomator.domain.usecases.LicenseCheck
import org.cryptomator.domain.usecases.NoOpResultHandler
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.ui.activity.view.UpdateLicenseView
import org.cryptomator.util.SharedPreferencesHandler
import javax.inject.Inject
import timber.log.Timber

class LicenseCheckPresenter @Inject internal constructor(
		exceptionHandlers: ExceptionHandlers,  //
		private val doLicenseCheckUsecase: DoLicenseCheckUseCase,  //
		private val sharedPreferencesHandler: SharedPreferencesHandler) : Presenter<UpdateLicenseView>(exceptionHandlers) {

	fun validate(data: Uri?) {
		data?.let {
			val license = it.fragment ?: it.lastPathSegment ?: ""
			view?.showOrUpdateLicenseDialog(license)
			doLicenseCheckUsecase
					.withLicense(license)
					.run(CheckLicenseStatusSubscriber())
		}
	}

	fun validateDialogAware(license: String?) {
		doLicenseCheckUsecase
				.withLicense(license)
				.run(CheckLicenseStatusSubscriber())
	}

	private inner class CheckLicenseStatusSubscriber : NoOpResultHandler<LicenseCheck>() {

		override fun onSuccess(licenseCheck: LicenseCheck) {
			super.onSuccess(licenseCheck)
			view?.closeDialog()
			Timber.tag("LicenseCheckPresenter").i("Your license is valid!")
			sharedPreferencesHandler.setMail(licenseCheck.mail())
			view?.showConfirmationDialog(licenseCheck.mail())
		}

		override fun onError(t: Throwable) {
			super.onError(t)
			showError(t)
		}
	}

	init {
		unsubscribeOnDestroy(doLicenseCheckUsecase)
	}
}
