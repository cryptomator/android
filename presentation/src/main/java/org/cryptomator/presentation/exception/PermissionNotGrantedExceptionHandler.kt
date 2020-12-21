package org.cryptomator.presentation.exception

import org.cryptomator.presentation.ui.activity.view.View
import org.cryptomator.presentation.ui.snackbar.AppSettingsAction
import org.cryptomator.util.ExceptionUtil

class PermissionNotGrantedExceptionHandler : ExceptionHandler() {

	override fun supports(e: Throwable): Boolean {
		return ExceptionUtil.contains(e, PermissionNotGrantedException::class.java)
	}

	override fun doHandle(view: View, e: Throwable) {
		val extract = ExceptionUtil.extract(e, PermissionNotGrantedException::class.java).get()
		view.showSnackbar(extract.snackbarText, AppSettingsAction(view.context()))
	}
}
