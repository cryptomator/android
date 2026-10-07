package org.cryptomator.presentation.ui.layout

import android.content.Context
import android.util.AttributeSet
import android.widget.RelativeLayout

class BottomToolbarLayout @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr) {

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()
		applySystemBarsMargins(end = true, bottom = true)
	}
}
