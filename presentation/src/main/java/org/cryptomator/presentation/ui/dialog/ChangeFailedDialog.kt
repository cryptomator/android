package org.cryptomator.presentation.ui.dialog

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.DialogChangeFailedBinding
import org.cryptomator.presentation.ui.activity.ErrorDisplay

/**
 * Tells that a change the browser already showed failed. The activity hands the reason to the shown dialog, possibly
 * before its view exists.
 */
@Dialog
class ChangeFailedDialog : BaseInformationalDialog<DialogChangeFailedBinding>(DialogChangeFailedBinding::inflate), ErrorDisplay {

	override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog = builder
		.setTitle(getString(R.string.dialog_change_failed_title, requireArguments().getString(NAME_ARG)))
		.setNeutralButton(getString(R.string.dialog_change_failed_positive_button)) { _, _ -> }
		.dismissOnBackKey()
		.create()

	override fun setupView() {
		binding.tvChangeFailedReason.text = requireArguments().getString(REASON_ARG)
	}

	override fun showError(messageId: Int) {
		showError(getString(messageId))
	}

	override fun showError(message: String) {
		requireArguments().putString(REASON_ARG, message)
		if (view != null) {
			binding.tvChangeFailedReason.text = message
		}
	}

	companion object {

		private const val NAME_ARG = "name"
		private const val REASON_ARG = "reason"

		fun newInstance(name: String) = ChangeFailedDialog().apply {
			arguments = Bundle().apply { putString(NAME_ARG, name) }
		}
	}
}
