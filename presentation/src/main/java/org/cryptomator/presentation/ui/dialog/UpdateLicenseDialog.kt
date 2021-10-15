package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.ui.layout.ObscuredAwareDialogCoordinatorLayout
import org.cryptomator.util.SharedPreferencesHandler
import kotlinx.android.synthetic.main.dialog_enter_license.dssialogRootView
import kotlinx.android.synthetic.main.dialog_enter_license.et_license

@Dialog(R.layout.dialog_enter_license)
class UpdateLicenseDialog : BaseProgressErrorDialog<UpdateLicenseDialog.Callback>() {

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
				callback?.checkLicenseClicked(et_license.text.toString())
				onWaitForResponse(et_license)
			}
			checkLicenseButton?.let { button ->
				et_license.nextFocusForwardId = button.id
			}
		}

		/* need to manually handle this in case of dialogs as otherwise the onFilterTouchEventForSecurity method of the ViewGroup
		isn't called when filterTouchesWhenObscured is set to true in the BaseDialog and in contrast to if set in an Activity */
		dialog?.window?.decorView?.filterTouchesWhenObscured = false
		dssialogRootView.setOnFilteredTouchEventForSecurityListener(object : ObscuredAwareDialogCoordinatorLayout.Listener {
			override fun onFilteredTouchEventForSecurity() {
				callback?.appObscuredClosingEnterLicenseDialog()
			}
		}, SharedPreferencesHandler(requireContext()).disableAppWhenObscured())
	}

	public override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		return builder //
			.setTitle(getString(R.string.dialog_enter_license_title)) //
			.setPositiveButton(getText(R.string.dialog_enter_license_ok_button)) { _: DialogInterface, _: Int -> } //
			.setNegativeButton(getText(R.string.dialog_enter_license_decline_button)) { _: DialogInterface, _: Int -> callback?.onCheckLicenseCanceled() } //
			.create()
	}

	public override fun setupView() {
		val license = requireArguments().getSerializable(LICENSE_ARG) as String?
		license?.let { et_license.setText(it) }
		et_license.requestFocus()
		checkLicenseButton?.let { registerOnEditorDoneActionAndPerformButtonClick(et_license) { it } }
	}

	override fun enableViewAfterError(): View {
		return et_license
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
