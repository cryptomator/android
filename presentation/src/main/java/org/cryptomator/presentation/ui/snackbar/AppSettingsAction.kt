package org.cryptomator.presentation.ui.snackbar

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.View
import org.cryptomator.presentation.R

class AppSettingsAction(private val context: Context) : SnackbarAction {

	override fun onClick(view: View) {
		val appSettings = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse(PACKAGE + context.packageName))
		appSettings.addCategory(Intent.CATEGORY_DEFAULT)
		appSettings.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
		context.startActivity(appSettings)
	}

	override val text: Int
		get() = R.string.snack_bar_action_title_settings

	companion object {
		private const val PACKAGE = "package:"
	}
}
