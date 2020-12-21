package org.cryptomator.presentation.ui.bottomsheet

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.cryptomator.presentation.R

abstract class BaseBottomSheet<Callback> : BottomSheetDialogFragment() {

	protected abstract fun setupView()

	var callback: Callback? = null

	override fun onAttach(context: Context) {
		super.onAttach(context)
		// Verify that the host activity implements the callback interface
		try {
			callback = context as Callback
		} catch (e: ClassCastException) {
			// The activity doesn't implement the interface, throw exception
			throw ClassCastException("$context must implement Callback")
		}
	}

	// Need to return the view here or onViewCreated won't be called by DialogFragment, sigh
	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return requireActivity().layoutInflater.inflate(bottomSheetContent, null, false)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		setupView()
	}

	override fun onResume() {
		super.onResume()
		if (isLandscape) {
			val width = requireContext().resources.getDimensionPixelSize(R.dimen.landscape_bottom_sheet_width)
			dialog?.window?.setLayout(width, ViewGroup.LayoutParams.MATCH_PARENT)
		}
	}

	private val isLandscape: Boolean
		get() = requireContext().resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

	private val bottomSheetContent: Int
		get() = javaClass.getAnnotation(org.cryptomator.generator.BottomSheet::class.java)!!.value
}
