package org.cryptomator.presentation.ui.bottomsheet

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.viewbinding.ViewBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.cryptomator.generator.BottomSheet
import org.cryptomator.presentation.BuildConfig
import org.cryptomator.presentation.R
import org.cryptomator.util.SharedPreferencesHandler

abstract class BaseBottomSheet<Callback, VB : ViewBinding>(val bindingFactory: (LayoutInflater, ViewGroup?, Boolean) -> VB) : BottomSheetDialogFragment() {

	protected abstract fun setupView()

	protected lateinit var binding: VB

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
		binding = bindingFactory(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		val config = javaClass.getAnnotation(BottomSheet::class.java)
		if (config?.secure == true && SharedPreferencesHandler(requireContext()).secureScreen() && !BuildConfig.DEBUG) {
			dialog?.window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
		} else {
			dialog?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
		}
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
