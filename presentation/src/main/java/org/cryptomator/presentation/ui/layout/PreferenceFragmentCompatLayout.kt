package org.cryptomator.presentation.ui.layout

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.RecyclerView

abstract class PreferenceFragmentCompatLayout : PreferenceFragmentCompat() {

	override fun onCreateRecyclerView(
		inflater: LayoutInflater,
		parent: ViewGroup,
		savedInstanceState: Bundle?
	): RecyclerView {
		val rv = super.onCreateRecyclerView(inflater, parent, savedInstanceState)
		rv.clipToPadding = false
		rv.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
		rv.applyPreferenceInsets(left = true, right = true, bottom = true)
		return rv
	}
}

