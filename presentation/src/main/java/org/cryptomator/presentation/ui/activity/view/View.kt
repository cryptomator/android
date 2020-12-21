package org.cryptomator.presentation.ui.activity.view

import androidx.fragment.app.DialogFragment
import org.cryptomator.presentation.presenter.ActivityHolder
import org.cryptomator.presentation.ui.activity.ErrorDisplay
import org.cryptomator.presentation.ui.activity.MessageDisplay
import org.cryptomator.presentation.ui.activity.ProgressAware
import org.cryptomator.presentation.ui.snackbar.SnackbarAction
import kotlin.reflect.KClass

interface View : ProgressAware, MessageDisplay, ErrorDisplay, ActivityHolder {

	fun showDialog(dialog: DialogFragment)
	fun isShowingDialog(dialog: KClass<out DialogFragment>): Boolean
	fun currentDialog(): DialogFragment?
	fun closeDialog()
	fun finish()
	fun showSnackbar(messageId: Int, action: SnackbarAction)

}
