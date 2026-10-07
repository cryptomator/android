package org.cryptomator.presentation.ui.dialog

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.ActivityEmptyBinding
import org.cryptomator.presentation.databinding.DialogGenericProgressBinding
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.model.ProgressStateModel
import org.cryptomator.presentation.ui.activity.BaseActivity
import org.cryptomator.presentation.ui.activity.ProgressAware

@Dialog
class GenericProgressDialog : BaseDialog<BaseActivity<ActivityEmptyBinding>, DialogGenericProgressBinding>(DialogGenericProgressBinding::inflate), ProgressAware {

	override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		return builder.create()
	}

	override fun setupView() {
		isCancelable = false
		binding.llDialogProgress.llProgress.visibility = View.VISIBLE
		enableOrientationChange(false)
		showProgress((requireArguments().getSerializable(INITIAL_PROGRESS) as ProgressModel))
	}

	override fun showProgress(progress: ProgressModel) {
		binding.llDialogProgress.tvProgress.setText(textFor(progress))
		if (progress.state() === ProgressStateModel.COMPLETED) {
			enableOrientationChange(true)
			callback?.closeDialog()
		}
	}

	private fun textFor(progress: ProgressModel): Int {
		val resourceId = progress.state().textResourceId()
		return if (resourceId == 0) {
			R.string.dialog_progress_please_wait
		} else resourceId
	}

	companion object {

		private const val INITIAL_PROGRESS = "initialProgress"
		fun create(progressModel: ProgressModel): GenericProgressDialog {
			val dialog = GenericProgressDialog()
			val args = Bundle()
			args.putSerializable(INITIAL_PROGRESS, progressModel)
			dialog.arguments = args
			return dialog
		}
	}
}
