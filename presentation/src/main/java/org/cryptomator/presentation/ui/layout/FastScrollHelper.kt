package org.cryptomator.presentation.ui.layout

import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ScrollView

/**
 * Wires [thumb] as a draggable fast-scroll handle over this [ScrollView] (whose scrolling content is [content]).
 * Tapping anywhere on [track] jumps the thumb to that position.
 * Returns a cleanup callback to be invoked from the host's `onDestroyView`.
 */
fun ScrollView.attachFastScrollThumb(thumb: View, track: View, content: View): () -> Unit {
	val scroll = this

	fun scrollableHeight(): Int =
		(content.height + scroll.paddingTop + scroll.paddingBottom - scroll.height).coerceAtLeast(0)

	fun trackHeight(): Int {
		val bottomMargin = (thumb.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
		return (scroll.height - thumb.height - bottomMargin).coerceAtLeast(0)
	}

	fun syncThumb() {
		val total = scrollableHeight()
		if (total == 0) {
			thumb.visibility = View.GONE
			track.visibility = View.GONE
			return
		}
		thumb.visibility = View.VISIBLE
		track.visibility = View.VISIBLE
		thumb.translationY = scroll.scrollY.toFloat() / total * trackHeight()
	}

	fun jumpToTrackY(yOnTrack: Float) {
		val trackPx = trackHeight().toFloat()
		if (trackPx == 0f) return
		val clamped = yOnTrack.coerceIn(0f, trackPx)
		scroll.scrollTo(0, (clamped / trackPx * scrollableHeight()).toInt())
	}

	val scrollListener = ViewTreeObserver.OnScrollChangedListener { syncThumb() }
	scroll.viewTreeObserver.addOnScrollChangedListener(scrollListener)

	val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> syncThumb() }
	scroll.addOnLayoutChangeListener(layoutListener)
	content.addOnLayoutChangeListener(layoutListener)

	var thumbDragOffsetY = 0f
	var thumbDragMoved = false
	thumb.isClickable = true
	thumb.setOnTouchListener { _, event ->
		when (event.actionMasked) {
			MotionEvent.ACTION_DOWN -> {
				thumbDragOffsetY = event.rawY - thumb.translationY
				thumbDragMoved = false
				thumb.isPressed = true
				true
			}
			MotionEvent.ACTION_MOVE -> {
				thumbDragMoved = true
				jumpToTrackY(event.rawY - thumbDragOffsetY)
				true
			}
			MotionEvent.ACTION_UP -> {
				thumb.isPressed = false
				if (!thumbDragMoved) thumb.performClick()
				true
			}
			MotionEvent.ACTION_CANCEL -> {
				thumb.isPressed = false
				true
			}
			else -> false
		}
	}

	track.isClickable = true
	track.setOnTouchListener { _, event ->
		when (event.actionMasked) {
			MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
				jumpToTrackY(event.y - thumb.height / 2f)
				true
			}
			MotionEvent.ACTION_UP -> {
				track.performClick()
				true
			}
			else -> false
		}
	}

	return {
		scroll.viewTreeObserver.removeOnScrollChangedListener(scrollListener)
		scroll.removeOnLayoutChangeListener(layoutListener)
		content.removeOnLayoutChangeListener(layoutListener)
		thumb.setOnTouchListener(null)
		track.setOnTouchListener(null)
	}
}
