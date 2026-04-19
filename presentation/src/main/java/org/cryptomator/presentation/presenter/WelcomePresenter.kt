package org.cryptomator.presentation.presenter

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import org.cryptomator.domain.di.PerView
import org.cryptomator.domain.usecases.DoLicenseCheckUseCase
import org.cryptomator.generator.Callback
import org.cryptomator.presentation.R
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.ui.activity.view.WelcomeView
import org.cryptomator.presentation.workflow.PermissionsResult
import org.cryptomator.util.SharedPreferencesHandler
import timber.log.Timber
import javax.inject.Inject

@PerView
class WelcomePresenter @Inject internal constructor(
	exceptionHandlers: ExceptionHandlers,
	doLicenseCheckUseCase: DoLicenseCheckUseCase,
	sharedPreferencesHandler: SharedPreferencesHandler
) : BaseLicensePresenter<WelcomeView>(exceptionHandlers, doLicenseCheckUseCase, sharedPreferencesHandler) {

	/**
	 * Requests the runtime notification permission and reports the result to the view.
	 *
	 * On Android versions up to S_V2 this treats the permission as granted and immediately notifies the view.
	 * On newer versions it prompts the user for `Manifest.permission.POST_NOTIFICATIONS` using the welcome permission callback and a snackbar message.
	 */
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

	/**
	 * Handles the result of the welcome notification permission request and notifies the view.
	 *
	 * Logs an error if the permission was not granted, then calls `view?.onNotificationPermissionResult` with the grant status.
	 *
	 * @param result The permission result containing whether the notification permission was granted.
	 */
	@Callback
	fun requestWelcomeNotificationPermission(result: PermissionsResult) {
		if (!result.granted()) {
			Timber.tag("WelcomePresenter").e("Notification permission not granted, notifications will not show")
		}
		view?.onNotificationPermissionResult(result.granted())
	}

	/**
	 * Attempts to open the system "Set new password" (screen lock) activity when requested.
	 *
	 * If `setScreenLock` is true, launches the device policy activity for setting a new password; if the activity is not available, a debug message is logged.
	 *
	 * @param setScreenLock Whether to start the system screen lock setup activity.
	fun onSetScreenLock(setScreenLock: Boolean) {
		if (setScreenLock) {
			try {
				view?.activity()?.startActivity(Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD))
			} catch (e: ActivityNotFoundException) {
				Timber.tag("WelcomePresenter").d(e, "Device Policy Manager not found")
			}
		}
	}

}
