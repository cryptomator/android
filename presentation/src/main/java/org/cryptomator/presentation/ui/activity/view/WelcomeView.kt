package org.cryptomator.presentation.ui.activity.view

interface WelcomeView : UpdateLicenseView {
	/**
 * Called when the user responds to the notification permission request.
 *
 * @param granted `true` if notification permission was granted, `false` otherwise.
 */
fun onNotificationPermissionResult(granted: Boolean)
}
