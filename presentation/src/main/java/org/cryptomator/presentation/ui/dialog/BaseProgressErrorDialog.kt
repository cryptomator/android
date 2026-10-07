package org.cryptomator.presentation.ui.dialog

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.viewbinding.ViewBinding
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.ViewDialogErrorBinding
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.model.ProgressStateModel
import org.cryptomator.presentation.ui.activity.BaseActivity
import org.cryptomator.presentation.ui.activity.ErrorDisplay
import org.cryptomator.presentation.ui.activity.ProgressAware
import org.cryptomator.presentation.util.FileNameValidator.Companion.isInvalidName

abstract class BaseProgressErrorDialog<Callback, VB : ViewBinding>(
	mainBindingFactory: (LayoutInflater, ViewGroup?, Boolean) -> VB
) : BaseDialog<Callback, VB>(mainBindingFactory), ProgressAware, ErrorDisplay {

	override fun showError(message: String) {
		dialogProgressLayout().visibility = View.GONE
		dialogErrorBinding().llError.visibility = View.VISIBLE
		dialogErrorBinding().tvError.text = message
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
			dialogErrorBinding().llError.visibility = View.GONE
		}
	}

	override fun showProgress(progress: ProgressModel) {
		if (progress.state() === ProgressStateModel.COMPLETED) {
			enableOrientationChange(true)
			(activity as BaseActivity<*>?)?.closeDialog()
		} else {
			dialogErrorBinding().llError.visibility = View.GONE
			dialogProgressLayout().visibility = View.VISIBLE
			dialogProgressTextView().setText(progress.state().textResourceId())
			updateProgress(progress.progress())
		}
	}

	abstract fun dialogProgressLayout(): LinearLayout
	abstract fun dialogProgressTextView(): TextView
	abstract fun dialogErrorBinding(): ViewDialogErrorBinding

	open fun updateProgress(progress: Int) {}
	open fun enableViewAfterError(): View? {
		// default empty
		return null
	}
}
