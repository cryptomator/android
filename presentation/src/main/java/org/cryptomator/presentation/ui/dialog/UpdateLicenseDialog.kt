package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.DialogEnterLicenseBinding
import org.cryptomator.presentation.databinding.ViewDialogErrorBinding
import org.cryptomator.presentation.ui.layout.ObscuredAwareDialogCoordinatorLayout
import org.cryptomator.util.SharedPreferencesHandler

@Dialog
class UpdateLicenseDialog : BaseProgressErrorDialog<UpdateLicenseDialog.Callback, DialogEnterLicenseBinding>(DialogEnterLicenseBinding::inflate) {

	// positive button
	private var checkLicenseButton: Button? = null

	interface Callback {

		fun checkLicenseClicked(license: String?)
		fun onCheckLicenseCanceled()
		fun appObscuredClosingEnterLicenseDialog()
	}

	override fun onStart() {
		super.onStart()
		allowClosingDialog(false)
		val dialog = dialog as AlertDialog?
		dialog?.let {
			checkLicenseButton = dialog.getButton(android.app.Dialog.BUTTON_POSITIVE)
			checkLicenseButton?.setOnClickListener {
				callback?.checkLicenseClicked(binding.etLicense.text.toString())
				onWaitForResponse(binding.etLicense)
			}
			checkLicenseButton?.let { button ->
				binding.etLicense.nextFocusForwardId = button.id
			}
			binding.tvMessage.setOnClickListener {
				Intent(Intent.ACTION_VIEW).let {
					it.data = Uri.parse("https://cryptomator.org/android/")
					startActivity(it)
				}
			}
		}

		/* need to manually handle this in case of dialogs as otherwise the onFilterTouchEventForSecurity method of the ViewGroup
		isn't called when filterTouchesWhenObscured is set to true in the BaseDialog and in contrast to if set in an Activity */
		dialog?.window?.decorView?.filterTouchesWhenObscured = false
		binding.dssialogRootView.setOnFilteredTouchEventForSecurityListener(object : ObscuredAwareDialogCoordinatorLayout.Listener {
			override fun onFilteredTouchEventForSecurity() {
				callback?.appObscuredClosingEnterLicenseDialog()
			}
		}, SharedPreferencesHandler(requireContext()).disableAppWhenObscured())
	}

	override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		return builder //
			.setTitle(getString(R.string.dialog_enter_license_title)) //
			.setPositiveButton(getText(R.string.dialog_enter_license_ok_button)) { _: DialogInterface, _: Int -> } //
			.setNegativeButton(getText(R.string.dialog_enter_license_decline_button)) { _: DialogInterface, _: Int -> callback?.onCheckLicenseCanceled() } //
			.create()
	}

	public override fun setupView() {
		val license = requireArguments().getSerializable(LICENSE_ARG) as String?
		license?.let { binding.etLicense.setText(it) }
		binding.etLicense.requestFocus()
		checkLicenseButton?.let { registerOnEditorDoneActionAndPerformButtonClick(binding.etLicense) { it } }
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
		return binding.etLicense
	}

	companion object {

		private const val LICENSE_ARG = "LICENSE"
		fun newInstance(license: String?): UpdateLicenseDialog {
			val dialog = UpdateLicenseDialog()
			val args = Bundle()
			args.putSerializable(LICENSE_ARG, license)
			dialog.arguments = args
			return dialog
		}

		fun newInstance(): UpdateLicenseDialog {
			return UpdateLicenseDialog()
		}
	}
}
