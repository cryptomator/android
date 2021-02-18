package org.cryptomator.presentation.ui.layout

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.coordinatorlayout.widget.CoordinatorLayout

class ObscuredAwareCoordinatorLayout : CoordinatorLayout {

	private var listener: Listener? = null

	constructor(context: Context) : super(context)
	constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
	constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

	fun setOnFilteredTouchEventForSecurityListener(listener: Listener?) {
		this.listener = listener
	}

	override fun onFilterTouchEventForSecurity(event: MotionEvent): Boolean {
		return if (super.onFilterTouchEventForSecurity(event)) {
			true
		} else {
			listener?.onFilteredTouchEventForSecurity()
			false
		}
	}

	interface Listener {

		fun onFilteredTouchEventForSecurity()
	}
}
