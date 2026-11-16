package org.cryptomator.presentation.ui.dialog

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.viewbinding.ViewBinding

abstract class BaseInformationalDialog<VB : ViewBinding>(
	bindingFactory: (LayoutInflater, ViewGroup?, Boolean) -> VB
) : BaseDialog<Any, VB>(bindingFactory) {

	override fun onStart() {
		super.onStart()
		(dialog as? AlertDialog)?.setCanceledOnTouchOutside(false)
	}

	override fun setupView() = Unit

	protected fun AlertDialog.Builder.dismissOnBackKey(): AlertDialog.Builder = setOnKeyListener { _, keyCode, _ ->
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			dialog?.dismiss()
			true
		} else {
			false
		}
	}
}
