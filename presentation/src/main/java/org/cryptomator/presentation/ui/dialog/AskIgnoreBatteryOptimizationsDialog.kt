package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.DialogAskIgnoreBatteryOptimizationsBinding

@Dialog
class AskIgnoreBatteryOptimizationsDialog : BaseDialog<AskIgnoreBatteryOptimizationsDialog.Callback, DialogAskIgnoreBatteryOptimizationsBinding>(DialogAskIgnoreBatteryOptimizationsBinding::inflate) {

	interface Callback {

		fun onAskIgnoreBatteryOptimizationsAccepted()
		fun onAskIgnoreBatteryOptimizationsRejected(askAgain: Boolean)
	}

	public override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		return builder //
			.setTitle(R.string.dialog_ask_ignore_battery_optimizations_title) //
			.setPositiveButton(getString(R.string.dialog_ask_ignore_battery_optimizations_neutral_button)) { _: DialogInterface, _: Int -> callback?.onAskIgnoreBatteryOptimizationsAccepted() } //
			.setNegativeButton(getString(R.string.dialog_ask_ignore_battery_optimizations_negative_button)) { _: DialogInterface, _: Int -> callback?.onAskIgnoreBatteryOptimizationsRejected(!binding.cbAskIgnoreBatteryOptimizations.isChecked) } //
			.create()
	}

	public override fun setupView() {
		binding.tvAskIgnoreBatteryOptimizations.text = String.format(
			getString(R.string.dialog_ask_ignore_battery_optimizations_hint), //
			getString(R.string.app_name), //
			getString(R.string.dialog_ask_ignore_battery_optimizations_neutral_button) //
		)
	}

	companion object {

		fun newInstance(): DialogFragment {
			return AskIgnoreBatteryOptimizationsDialog()
		}
	}
}
