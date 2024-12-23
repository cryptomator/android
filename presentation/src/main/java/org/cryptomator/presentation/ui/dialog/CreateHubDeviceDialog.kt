package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.cryptomator.domain.UnverifiedHubVaultConfig
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.DialogCreateHubDeviceBinding
import org.cryptomator.presentation.databinding.ViewDialogErrorBinding
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.model.ProgressStateModel
import org.cryptomator.presentation.model.VaultModel

@Dialog
class CreateHubDeviceDialog : BaseProgressErrorDialog<CreateHubDeviceDialog.Callback, DialogCreateHubDeviceBinding>(DialogCreateHubDeviceBinding::inflate) {

	interface Callback {

		fun onCreateHubDeviceClicked(vaultModel: VaultModel, unverifiedVaultConfig: UnverifiedHubVaultConfig, deviceName: String, setupCode: String)
		fun onCreateHubDeviceCanceled()
	}

	override fun onStart() {
		super.onStart()
		val dialog = dialog as AlertDialog?
		dialog?.let {
			val createDeviceButton = dialog.getButton(android.app.Dialog.BUTTON_POSITIVE)
			createDeviceButton?.setOnClickListener {
				val vaultModel = requireArguments().getSerializable(VAULT_ARG) as VaultModel
				val unverifiedVaultConfig = requireArguments().getSerializable(VAULT_CONFIG_ARG) as UnverifiedHubVaultConfig
				if (valid(binding.etDeviceName.text.toString(), binding.etSetupCode.text.toString())) {
					showProgress(ProgressModel(ProgressStateModel.CREATING_HUB_DEVICE))
					callback?.onCreateHubDeviceClicked(vaultModel, unverifiedVaultConfig, binding.etDeviceName.text.toString(), binding.etSetupCode.text.toString())
					onWaitForResponse(binding.etDeviceName)
				}
			}
			dialog.setCanceledOnTouchOutside(false)
			dialog.setOnKeyListener { _, keyCode, _ ->
				if (keyCode == KeyEvent.KEYCODE_BACK) {
					dialog.dismiss()
					callback?.onCreateHubDeviceCanceled()
					true
				} else {
					false
				}
			}
			binding.etDeviceName.requestFocus()
			binding.etDeviceName.nextFocusForwardId = binding.etSetupCode.id
			createDeviceButton?.let {
				binding.etSetupCode.nextFocusForwardId = it.id
				registerOnEditorDoneActionAndPerformButtonClick(binding.etSetupCode) { it }
			}
		}
	}

	private fun valid(name: String, setupCode: String): Boolean {
		return when {
			name.isEmpty() -> {
				showError(R.string.dialog_create_hub_device_name_empty)
				false
			}
			setupCode.isEmpty() -> {
				showError(R.string.dialog_create_hub_device_setup_code_empty)
				false
			}
			else -> true
		}
	}

	override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		return builder.setTitle(requireContext().getString(R.string.dialog_create_hub_device_title))
			.setPositiveButton(requireContext().getString(R.string.dialog_create_hub_device_positive_button)) { _: DialogInterface, _: Int -> }
			.setNegativeButton(requireContext().getString(R.string.dialog_button_cancel)) { _: DialogInterface, _: Int -> callback?.onCreateHubDeviceCanceled() }
			.create()
	}

	override fun setupView() {
	}

	override fun dialogProgressLayout(): LinearLayout {
		return binding.llDialogProgress.llProgress
	}

	override fun dialogProgressTextView(): TextView {
		return binding.llDialogProgress.tvProgress
	}

	override fun dialogErrorBinding(): ViewDialogErrorBinding {
		return binding.llDialogError
	}

	override fun enableViewAfterError(): View {
		return binding.etDeviceName
	}

	companion object {

		private const val VAULT_ARG = "vault"
		private const val VAULT_CONFIG_ARG = "vaultConfig"
		fun newInstance(vaultModel: VaultModel, unverifiedVaultConfig: UnverifiedHubVaultConfig): CreateHubDeviceDialog {
			val args = Bundle()
			args.putSerializable(VAULT_ARG, vaultModel)
			args.putSerializable(VAULT_CONFIG_ARG, unverifiedVaultConfig)
			val fragment = CreateHubDeviceDialog()
			fragment.arguments = args
			return fragment
		}
	}

}
