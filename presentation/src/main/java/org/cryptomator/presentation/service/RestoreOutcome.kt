package org.cryptomator.presentation.service

import androidx.fragment.app.DialogFragment
import org.cryptomator.presentation.ui.dialog.NoFullVersionDialog
import org.cryptomator.presentation.ui.dialog.RestoreFailedDialog
import org.cryptomator.presentation.ui.dialog.RestoreSuccessfulDialog

sealed interface RestoreOutcome {
	data object RESTORED : RestoreOutcome
	data object NOTHING_TO_RESTORE : RestoreOutcome
	data class FAILED(val cause: Throwable? = null) : RestoreOutcome
}

fun RestoreOutcome.toDialogFragment(): DialogFragment = when (this) {
	RestoreOutcome.RESTORED -> RestoreSuccessfulDialog()
	RestoreOutcome.NOTHING_TO_RESTORE -> NoFullVersionDialog()
	is RestoreOutcome.FAILED -> RestoreFailedDialog()
}
