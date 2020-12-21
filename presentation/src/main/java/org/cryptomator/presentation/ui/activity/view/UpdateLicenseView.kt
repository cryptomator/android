package org.cryptomator.presentation.ui.activity.view

interface UpdateLicenseView : View {

	fun showOrUpdateLicenseDialog(license: String)
	fun showConfirmationDialog(mail: String)

}
