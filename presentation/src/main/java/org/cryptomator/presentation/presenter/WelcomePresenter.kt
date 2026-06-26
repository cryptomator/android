package org.cryptomator.presentation.presenter

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import org.cryptomator.domain.di.PerView
import org.cryptomator.domain.usecases.DoLicenseCheckUseCase
import org.cryptomator.generator.Callback
import org.cryptomator.presentation.R
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.ui.activity.view.WelcomeView
import org.cryptomator.presentation.ui.dialog.AppIsObscuredInfoDialog
import org.cryptomator.presentation.workflow.PermissionsResult
import org.cryptomator.util.SharedPreferencesHandler
import timber.log.Timber
import javax.inject.Inject

@PerView
class WelcomePresenter @Inject internal constructor(
	exceptionHandlers: ExceptionHandlers,
	doLicenseCheckUseCase: DoLicenseCheckUseCase,
	sharedPreferencesHandler: SharedPreferencesHandler
) : Presenter<WelcomeView>(exceptionHandlers) {

	private val validator = LicenseKeyValidator(doLicenseCheckUseCase, sharedPreferencesHandler, { view }, ::showError)

	fun validate(data: Uri?) = validator.validate(data) { view?.showOrUpdateLicenseEntry(it) }
	fun validateDialogAware(license: String?) = validator.validateDialogAware(license)

	fun onFilteredTouchEventForSecurity() {
		view?.showDialog(AppIsObscuredInfoDialog.newInstance())
	}

	fun requestNotificationPermission() {
		if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
			view?.onNotificationPermissionResult(true)
			return
		}
		requestPermissions(
			PermissionsResultCallbacks.requestWelcomeNotificationPermission(),
			R.string.permission_snackbar_notifications,
			Manifest.permission.POST_NOTIFICATIONS
		)
	}

	@Callback
	fun requestWelcomeNotificationPermission(result: PermissionsResult) {
		if (!result.granted()) {
			Timber.tag("WelcomePresenter").e("Notification permission not granted, notifications will not show")
		}
		view?.onNotificationPermissionResult(result.granted())
	}

	fun onSetScreenLock(setScreenLock: Boolean) {
		if (setScreenLock) {
			try {
				view?.activity()?.startActivity(Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD))
			} catch (e: ActivityNotFoundException) {
				Timber.tag("WelcomePresenter").d(e, "Device Policy Manager not found")
			}
		}
	}

	init {
		unsubscribeOnDestroy(doLicenseCheckUseCase)
	}
}
