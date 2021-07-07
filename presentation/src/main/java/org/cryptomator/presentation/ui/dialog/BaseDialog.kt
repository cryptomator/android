package org.cryptomator.presentation.ui.dialog

import android.app.Dialog
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import org.cryptomator.presentation.util.KeyboardHelper
import org.cryptomator.util.SharedPreferencesHandler
import java.util.function.Supplier

abstract class BaseDialog<Callback> : DialogFragment() {

	private lateinit var customDialog: View

	var callback: Callback? = null

	protected abstract fun setupDialog(builder: AlertDialog.Builder): Dialog
	protected abstract fun setupView()

	fun show(fragmentManager: FragmentManager) {
		show(fragmentManager, javaClass.simpleName)
	}

	override fun onAttach(context: Context) {
		super.onAttach(context)
		callback = context as Callback
	}

	override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
		val builder = AlertDialog.Builder(requireActivity())
		customDialog = requireActivity().layoutInflater.inflate(dialogContent, null)
		builder.setView(customDialog)
		val dialog = setupDialog(builder)
		dialog.window?.decorView?.filterTouchesWhenObscured = disableDialogWhenObscured()
		return dialog
	}

	// Need to return the view here or onViewCreated won't be called by DialogFragment, sigh
	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return customDialog
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		setupView()
	}

	protected open fun disableDialogWhenObscured(): Boolean {
		return SharedPreferencesHandler(requireContext()).disableAppWhenObscured()
	}

	fun onWaitForResponse(view: View) {
		view.isFocusable = false
		allowClosingDialog(false)
		enableButtons(false)
		hideKeyboard(view)
		enableOrientationChange(false)
	}

	fun enableOrientationChange(enable: Boolean) {
		if (enable) {
			requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
		} else {
			requireActivity().requestedOrientation = if (isLandscape) //
				ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else  //
				ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
		}
	}

	private val isLandscape: Boolean
		get() = requireContext().resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

	fun onErrorResponse(view: View?) {
		view?.let { it.isFocusableInTouchMode = true }
		allowClosingDialog(true)
		enableButtons(true)
	}

	private fun enableButtons(enabled: Boolean) {
		val dialog = dialog as AlertDialog?
		dialog?.getButton(Dialog.BUTTON_POSITIVE)?.isEnabled = enabled
		dialog?.getButton(Dialog.BUTTON_NEGATIVE)?.isEnabled = enabled
	}

	fun allowClosingDialog(allow: Boolean) {
		// prevent closing the dialog on back key press
		dialog?.setCancelable(allow)
		// prevent closing the dialog on touch events outside the dialog
		dialog?.setCanceledOnTouchOutside(allow)
	}

	fun showKeyboard(dialog: Dialog) {
		KeyboardHelper.showKeyboardForDialog(dialog)
	}

	protected fun hideKeyboard(view: View) {
		KeyboardHelper.hideKeyboard(requireActivity(), view)
	}

	private val dialogContent: Int
		get() = javaClass.getAnnotation(org.cryptomator.generator.Dialog::class.java)!!.value

	protected fun registerOnEditorDoneActionAndPerformButtonClick(editText: EditText, positiveButton: Supplier<Button>) {
		editText.setOnEditorActionListener { _: TextView?, actionId: Int, _: KeyEvent? ->
			if (actionId == EditorInfo.IME_ACTION_DONE && positiveButton.get().isEnabled) {
				positiveButton.get().performClick()
			}
			false
		}
	}
}
