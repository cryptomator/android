package org.cryptomator.presentation.ui.layout

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.coordinatorlayout.widget.CoordinatorLayout

class ObscuredAwareDialogCoordinatorLayout : CoordinatorLayout {

	private var listener: Listener? = null

	private var active: Boolean = true

	constructor(context: Context) : super(context)

	constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

	constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

	fun setOnFilteredTouchEventForSecurityListener(listener: Listener, active: Boolean) {
		this.listener = listener
		this.active = active
	}

	override fun onFilterTouchEventForSecurity(event: MotionEvent): Boolean {
		return if (active and ((event.flags and MotionEvent.FLAG_WINDOW_IS_OBSCURED) == MotionEvent.FLAG_WINDOW_IS_OBSCURED)) {
			listener?.onFilteredTouchEventForSecurity()
			false
		} else {
			true
		}
	}

	interface Listener {

		fun onFilteredTouchEventForSecurity()
	}
}
