package org.cryptomator.presentation.util

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.viewpager.widget.ViewPager

/**
 * Workaround for https://github.com/cryptomator/android/issues/429
 * Source https://github.com/Baseflow/PhotoView/issues/31#issuecomment-19803926
 */
class ViewPagerWorkaround : ViewPager {

	constructor(context: Context) : super(context)
	constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

	override fun onTouchEvent(ev: MotionEvent?): Boolean {
		return try {
			super.onTouchEvent(ev)
		} catch (ex: IllegalArgumentException) {
			false
		}
	}

	override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
		return try {
			super.onInterceptTouchEvent(ev)
		} catch (ex: IllegalArgumentException) {
			false
		}
	}
}
