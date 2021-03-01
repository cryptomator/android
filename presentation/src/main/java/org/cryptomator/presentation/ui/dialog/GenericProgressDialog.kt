package org.cryptomator.presentation.ui.dialog

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.model.ProgressStateModel
import org.cryptomator.presentation.ui.activity.BaseActivity
import org.cryptomator.presentation.ui.activity.ProgressAware
import kotlinx.android.synthetic.main.view_dialog_progress.ll_progress
import kotlinx.android.synthetic.main.view_dialog_progress.tv_progress

@Dialog(R.layout.dialog_generic_progress)
class GenericProgressDialog : BaseDialog<BaseActivity>(), ProgressAware {

	override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		return builder.create()
	}

	override fun setupView() {
		isCancelable = false
		ll_progress.visibility = View.VISIBLE
		enableOrientationChange(false)
		showProgress((requireArguments().getSerializable(INITIAL_PROGRESS) as ProgressModel))
	}

	override fun showProgress(progress: ProgressModel) {
		tv_progress.setText(textFor(progress))
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
