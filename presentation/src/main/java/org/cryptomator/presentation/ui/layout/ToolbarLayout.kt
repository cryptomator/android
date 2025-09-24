package org.cryptomator.presentation.ui.layout

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.appbar.MaterialToolbar

class ToolbarLayout @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = com.google.android.material.R.attr.toolbarStyle
) : MaterialToolbar(context, attrs, defStyleAttr) {

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()
		this.applySystemBarsPadding(left = true, top = true, right = true)
	}
}
