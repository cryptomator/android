package org.cryptomator.presentation.ui.layout

import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.max

/** Apply system bar/IME insets as *padding* on any View (e.g., toolbars, bottom bars, scroll containers). */
fun View.applySystemBarsPadding(
	left: Boolean = false,
	top: Boolean = false,
	right: Boolean = false,
	bottom: Boolean = false,
	includeIme: Boolean = true
) {
	val baseL = paddingLeft
	val baseT = paddingTop
	val baseR = paddingRight
	val baseB = paddingBottom

	ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
		val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
		val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
		val btm = if (includeIme) max(sys.bottom, ime.bottom) else sys.bottom
		v.updatePadding(
			left = if (left) baseL + sys.left else baseL,
			top = if (top) baseT + sys.top else baseT,
			right = if (right) baseR + sys.right else baseR,
			bottom = if (bottom) baseB + btm else baseB
		)
		insets
	}
	requestApplyInsetsWhenAttached()
}

/** Apply system bar/IME insets as *margins* (useful for floating views like FABs). */
fun View.applySystemBarsMargins(
	start: Boolean = false,
	top: Boolean = false,
	end: Boolean = false,
	bottom: Boolean = false,
	includeIme: Boolean = true
) {
	val lp = layoutParams as? ViewGroup.MarginLayoutParams ?: return
	val baseStart = lp.marginStart
	val baseEnd = lp.marginEnd
	val baseTop = lp.topMargin
	val baseBottom = lp.bottomMargin

	ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
		val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
		val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
		val btm = if (includeIme) max(sys.bottom, ime.bottom) else sys.bottom

		if (start) lp.marginStart = baseStart + sys.left
		if (end) lp.marginEnd = baseEnd + sys.right
		if (top) lp.topMargin = baseTop + sys.top
		if (bottom) lp.bottomMargin = baseBottom + btm

		v.layoutParams = lp
		insets
	}
	requestApplyInsetsWhenAttached()
}

/** Helper for preferences */
fun RecyclerView.applyPreferenceInsets(
	left: Boolean = true,
	right: Boolean = true,
	bottom: Boolean = true,
	includeIme: Boolean = true
) {
	clipToPadding = false
	scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
	applySystemBarsPadding(left = left, right = right, bottom = bottom, includeIme = includeIme)
}

/** Ensure we receive insets once attached. */
private fun View.requestApplyInsetsWhenAttached() {
	if (isAttachedToWindow) requestApplyInsets() else addOnAttachStateChangeListener(
		object : View.OnAttachStateChangeListener {
			override fun onViewAttachedToWindow(v: View) {
				v.removeOnAttachStateChangeListener(this); v.requestApplyInsets()
			}

			override fun onViewDetachedFromWindow(v: View) {}
		}
	)
}
