package org.cryptomator.presentation.presenter

import android.net.Uri
import org.cryptomator.domain.usecases.DoLicenseCheckUseCase
import org.cryptomator.domain.usecases.LicenseCheck
import org.cryptomator.domain.usecases.NoOpResultHandler
import org.cryptomator.presentation.ui.activity.view.LicenseView
import org.cryptomator.util.SharedPreferencesHandler

internal class LicenseKeyValidator(
	private val doLicenseCheckUseCase: DoLicenseCheckUseCase,
	private val sharedPreferencesHandler: SharedPreferencesHandler,
	private val getView: () -> LicenseView?,
	private val onError: (Throwable) -> Unit
) {
	fun validate(data: Uri?, onLicenseExtracted: ((String) -> Unit)? = null) {
		data?.let {
			val license = it.fragment ?: it.lastPathSegment
			if (license.isNullOrEmpty()) {
				return
			}
			onLicenseExtracted?.invoke(license)
			doLicenseCheckUseCase.withLicense(license).run(subscriber())
		}
	}

	fun validateDialogAware(license: String?) {
		doLicenseCheckUseCase.withLicense(license).run(subscriber())
	}

	private fun subscriber(): NoOpResultHandler<LicenseCheck> = object : NoOpResultHandler<LicenseCheck>() {
		override fun onSuccess(licenseCheck: LicenseCheck) {
			super.onSuccess(licenseCheck)
			getView()?.closeDialog()
			sharedPreferencesHandler.setMail(licenseCheck.mail())
			getView()?.showConfirmationDialog(licenseCheck.mail())
		}

		override fun onError(t: Throwable) {
			super.onError(t)
			this@LicenseKeyValidator.onError(t)
		}
	}
}
