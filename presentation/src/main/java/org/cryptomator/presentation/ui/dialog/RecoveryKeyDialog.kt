package org.cryptomator.presentation.ui.dialog

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.DialogRecoveryKeyBinding

@Dialog(secure = true)
class RecoveryKeyDialog : BaseDialog<RecoveryKeyDialog.Callback, DialogRecoveryKeyBinding>(DialogRecoveryKeyBinding::inflate) {

	interface Callback {

		fun onRecoveryKeyDialogFinished()
	}

	override fun onStart() {
		super.onStart()
		val dialog = dialog as AlertDialog?
		dialog?.setCanceledOnTouchOutside(false)
		// set here instead of in setupDialog because copying must not dismiss the dialog
		dialog?.getButton(android.app.Dialog.BUTTON_NEUTRAL)?.setOnClickListener { copyRecoveryKey() }
	}

	private fun copyRecoveryKey() {
		val clip = ClipData.newPlainText(getString(R.string.dialog_recovery_key_title), recoveryKey())
		clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
		val clipboardManager = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
		clipboardManager.setPrimaryClip(clip)
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
			// newer versions confirm the copy themselves
			Toast.makeText(requireContext(), R.string.dialog_recovery_key_copied, Toast.LENGTH_SHORT).show()
		}
	}

	override fun onCancel(dialog: DialogInterface) {
		super.onCancel(dialog)
		callback?.onRecoveryKeyDialogFinished()
	}

	override fun onDismiss(dialog: DialogInterface) {
		super.onDismiss(dialog)
		enableOrientationChange(true)
	}

	public override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		return builder //
			.setTitle(R.string.dialog_recovery_key_title) //
			.setPositiveButton(getString(R.string.dialog_recovery_key_positive_button)) { _: DialogInterface, _: Int -> callback?.onRecoveryKeyDialogFinished() } //
			.setNeutralButton(getString(R.string.dialog_recovery_key_copy_button)) { _: DialogInterface, _: Int -> } //
			.create()
	}

	private fun recoveryKey(): String {
		return requireArguments().getString(RECOVERY_KEY_ARG)!!
	}

	public override fun setupView() {
		// a recreated activity closes its dialogs, so rotating the device would take the recovery key away
		enableOrientationChange(false)
		binding.tvDescription.text = getString(R.string.dialog_recovery_key_description, requireArguments().getString(VAULT_NAME_ARG))
		binding.tvRecoveryKey.text = recoveryKey()
	}

	companion object {

		private const val VAULT_NAME_ARG = "vaultName"
		private const val RECOVERY_KEY_ARG = "recoveryKey"
		fun newInstance(vaultName: String, recoveryKey: String): RecoveryKeyDialog {
			val args = Bundle()
			args.putString(VAULT_NAME_ARG, vaultName)
			args.putString(RECOVERY_KEY_ARG, recoveryKey)
			val fragment = RecoveryKeyDialog()
			fragment.arguments = args
			return fragment
		}
	}
}
