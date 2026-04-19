package org.cryptomator.presentation.ui.activity.view

interface UpdateLicenseView : View {

	/**
 * Displays or updates a license entry in the UI for the given license text.
 *
 * @param license The license text to display or use to update the existing license entry.
 */
fun showOrUpdateLicenseEntry(license: String)
	/**
 * Presents a confirmation dialog related to the given email address.
 *
 * @param mail The email address to display or confirm in the dialog.
 */
fun showConfirmationDialog(mail: String)

}
