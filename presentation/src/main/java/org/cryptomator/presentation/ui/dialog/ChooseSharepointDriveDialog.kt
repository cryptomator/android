package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import android.os.Bundle
import android.widget.RadioButton
import androidx.appcompat.app.AlertDialog
import org.cryptomator.domain.SharepointCloud
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.DialogChooseSharepointDriveBinding

@Dialog
class ChooseSharepointDriveDialog : BaseDialog<ChooseSharepointDriveDialog.Callback, DialogChooseSharepointDriveBinding>(DialogChooseSharepointDriveBinding::inflate) {

	interface Callback {

		fun onSharepointDriveChosen(cloud: SharepointCloud)
	}

	override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		return builder.setTitle(R.string.dialog_choose_sharepoint_drive_title)
			.setNegativeButton(R.string.dialog_choose_sharepoint_drive_negative_button) { _: DialogInterface, _: Int -> }
			.create()
	}

	override fun setupView() {
		val drives = requireArguments().getSerializable(ARG_DRIVES) as ArrayList<SharepointCloud>
		drives.forEach { drive ->
			val radioButton = RadioButton(requireContext())
			radioButton.text = drive.driveName()
			radioButton.setOnClickListener {
				callback?.onSharepointDriveChosen(drive)
				dismiss()
			}
			binding.rgSharepointDrives.addView(radioButton)
		}
	}

	companion object {

		private const val ARG_DRIVES = "drives"
		fun newInstance(drives: ArrayList<SharepointCloud>): ChooseSharepointDriveDialog {
			val args = Bundle()
			args.putSerializable(ARG_DRIVES, drives)
			val fragment = ChooseSharepointDriveDialog()
			fragment.arguments = args
			return fragment
		}
	}
}
