package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import android.view.KeyEvent
import androidx.appcompat.app.AlertDialog
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.DialogHubLicenseUpgradeRequiredBinding

@Dialog
class HubLicenseUpgradeRequiredDialog : BaseDialog<HubLicenseUpgradeRequiredDialog.Callback, DialogHubLicenseUpgradeRequiredBinding>(DialogHubLicenseUpgradeRequiredBinding::inflate) {

	interface Callback {

		fun onHubLicenseUpgradeRequiredDialogFinished()
	}

	public override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		builder //
			.setTitle(R.string.dialog_hub_license_upgrade_required_title) //
			.setNeutralButton(getString(R.string.dialog_hub_license_upgrade_required_neutral_button)) { _: DialogInterface, _: Int -> callback?.onHubLicenseUpgradeRequiredDialogFinished() }
			.setOnKeyListener { _, keyCode, _ ->
				if (keyCode == KeyEvent.KEYCODE_BACK) {
					dialog?.dismiss()
					callback?.onHubLicenseUpgradeRequiredDialogFinished()
					true
				} else {
					false
				}
			}
		return builder.create()
	}

	public override fun setupView() {
	}

	companion object {

		fun newInstance(): HubLicenseUpgradeRequiredDialog {
			return HubLicenseUpgradeRequiredDialog()
		}
	}
}
