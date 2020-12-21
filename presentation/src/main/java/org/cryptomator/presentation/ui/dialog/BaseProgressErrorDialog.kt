package org.cryptomator.presentation.ui.dialog

import android.view.View
import kotlinx.android.synthetic.main.view_dialog_error.*
import kotlinx.android.synthetic.main.view_dialog_progress.*
import org.cryptomator.presentation.R
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.model.ProgressStateModel
import org.cryptomator.presentation.ui.activity.BaseActivity
import org.cryptomator.presentation.ui.activity.ErrorDisplay
import org.cryptomator.presentation.ui.activity.ProgressAware
import org.cryptomator.presentation.util.FileNameValidator.Companion.isInvalidName

abstract class BaseProgressErrorDialog<Callback> : BaseDialog<Callback>(), ProgressAware, ErrorDisplay {

	override fun showError(message: String) {
		ll_progress.visibility = View.GONE
		ll_error.visibility = View.VISIBLE
		tv_error.text = message
		enableOrientationChange(true)
		onErrorResponse(enableViewAfterError())
	}

	fun hasInvalidInput(input: String): Boolean {
		return isInvalidName(input)
	}

	override fun showError(messageId: Int) {
		showError(getString(messageId))
	}

	fun validateInput(input: String) {
		if (hasInvalidInput(input)) {
			showError(R.string.error_name_contains_invalid_characters)
		} else {
			ll_error.visibility = View.GONE
		}
	}

	override fun showProgress(progress: ProgressModel) {
		if (progress.state() === ProgressStateModel.COMPLETED) {
			enableOrientationChange(true)
			(activity as BaseActivity?)?.closeDialog()
		} else {
			ll_error.visibility = View.GONE
			ll_progress.visibility = View.VISIBLE
			tv_progress.setText(progress.state().textResourceId())
			updateProgress(progress.progress())
		}
	}

	open fun updateProgress(progress: Int) {}
	open fun enableViewAfterError(): View? {
		// default empty
		return null
	}
}
