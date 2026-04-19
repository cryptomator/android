package org.cryptomator.presentation.presenter

import android.net.Uri
import org.cryptomator.domain.usecases.DoLicenseCheckUseCase
import org.cryptomator.domain.usecases.LicenseCheck
import org.cryptomator.domain.usecases.NoOpResultHandler
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.ui.activity.view.UpdateLicenseView
import org.cryptomator.presentation.ui.dialog.AppIsObscuredInfoDialog
import org.cryptomator.util.SharedPreferencesHandler
import javax.inject.Inject

open class BaseLicensePresenter<V : UpdateLicenseView> @Inject internal constructor(
	exceptionHandlers: ExceptionHandlers,
	private val doLicenseCheckUseCase: DoLicenseCheckUseCase,
	protected val sharedPreferencesHandler: SharedPreferencesHandler
) : Presenter<V>(exceptionHandlers) {

	/**
	 * Validates and triggers a license status check for a license encoded in the given Uri.
	 *
	 * If `data` is null or does not contain a license fragment or last path segment, the method returns
	 * without performing any action. Otherwise it extracts the license (preferring the URI fragment,
	 * then the last path segment), updates the view's license entry, and starts a license status check.
	 *
	 * @param data A Uri that encodes the license either as its fragment or as its last path segment.
	 */
	fun validate(data: Uri?) {
		data?.let {
			val license = it.fragment ?: it.lastPathSegment
			if (license.isNullOrEmpty()) {
				return
			}
			view?.showOrUpdateLicenseEntry(license)
			doLicenseCheckUseCase
				.withLicense(license)
				.run(CheckLicenseStatusSubscriber())
		}
	}

	/**
	 * Initiates a license status check for the provided license string.
	 *
	 * @param license The license string to check, or `null` if no license is provided.
	 */
	fun validateDialogAware(license: String?) {
		doLicenseCheckUseCase
			.withLicense(license)
			.run(CheckLicenseStatusSubscriber())
	}

	/**
	 * Shows an obscuration information dialog when a security-related filtered touch event is detected.
	 */
	fun onFilteredTouchEventForSecurity() {
		view?.showDialog(AppIsObscuredInfoDialog.newInstance())
	}

	private inner class CheckLicenseStatusSubscriber : NoOpResultHandler<LicenseCheck>() {

		/**
		 * Handles a successful license check by persisting the returned mail, closing any open dialog, and showing a confirmation dialog with the mail.
		 *
		 * @param licenseCheck The license check result whose `mail()` value will be saved and displayed. 
		 */
		override fun onSuccess(licenseCheck: LicenseCheck) {
			super.onSuccess(licenseCheck)
			view?.closeDialog()
			sharedPreferencesHandler.setMail(licenseCheck.mail())
			view?.showConfirmationDialog(licenseCheck.mail())
		}

		/**
		 * Handles a license-check failure by displaying the provided error.
		 *
		 * @param t The throwable representing the error that occurred during the license check.
		 */
		override fun onError(t: Throwable) {
			super.onError(t)
			showError(t)
		}
	}

	init {
		unsubscribeOnDestroy(doLicenseCheckUseCase)
	}
}
