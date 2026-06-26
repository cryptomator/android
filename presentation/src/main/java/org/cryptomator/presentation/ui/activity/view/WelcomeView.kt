package org.cryptomator.presentation.ui.activity.view

interface WelcomeView : UpdateLicenseView {
	fun onNotificationPermissionResult(granted: Boolean)
}
