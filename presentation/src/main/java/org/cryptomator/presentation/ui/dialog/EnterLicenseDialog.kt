package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
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
class EnterLicenseDialog : BaseProgressErrorDialog<EnterLicenseDialog.Callback, DialogEnterLicenseBinding>(DialogEnterLicenseBinding::inflate) {

	private var checkLicenseButton: Button? = null

	interface Callback {
		fun onLicenseEntered(license: String)
	}

	override fun onStart() {
		super.onStart()
		val alertDialog = dialog as AlertDialog?
		alertDialog?.let {
			checkLicenseButton = it.getButton(android.app.Dialog.BUTTON_POSITIVE)
			checkLicenseButton?.isEnabled = false
			checkLicenseButton?.setOnClickListener {
				callback?.onLicenseEntered(binding.etLicense.text.toString())
				onWaitForResponse(binding.etLicense)
			}
			checkLicenseButton?.let { button ->
				binding.etLicense.nextFocusForwardId = button.id
			}
			binding.tvMessage.setOnClickListener {
				startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://cryptomator.org/android/")))
			}
			registerOnEditorDoneActionAndPerformButtonClick(binding.etLicense) { checkLicenseButton!! }
		}

		alertDialog?.window?.decorView?.filterTouchesWhenObscured = false
		binding.dssialogRootView.setOnFilteredTouchEventForSecurityListener(object : ObscuredAwareDialogCoordinatorLayout.Listener {
			override fun onFilteredTouchEventForSecurity() {
				dismiss()
			}
		}, SharedPreferencesHandler(requireContext()).disableAppWhenObscured())
	}

	override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		return builder
			.setTitle(R.string.dialog_enter_license_title)
			.setPositiveButton(R.string.dialog_enter_license_ok_button) { _: DialogInterface, _: Int -> }
			.setNegativeButton(R.string.dialog_enter_license_decline_button, null)
			.create()
	}

	override fun setupView() {
		binding.etLicense.requestFocus()
		binding.etLicense.addTextChangedListener(object : TextWatcher {
			override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
			override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
			override fun afterTextChanged(s: Editable) {
				checkLicenseButton?.isEnabled = s.isNotEmpty()
			}
		})
	}

	override fun dialogProgressLayout(): LinearLayout = binding.llDialogProgress.llProgress
	override fun dialogProgressTextView(): TextView = binding.llDialogProgress.tvProgress
	override fun dialogErrorBinding(): ViewDialogErrorBinding = binding.llDialogError
	override fun enableViewAfterError(): View = binding.etLicense

	companion object {
		fun newInstance(): EnterLicenseDialog = EnterLicenseDialog()
	}
}
