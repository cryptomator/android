package org.cryptomator.presentation.ui.adapter

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

class VaultsMoveListener(val adapter: VaultsAdapter) : ItemTouchHelper.Callback() {

	override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
		val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
		return makeMovementFlags(dragFlags, 0)
	}

	override fun isItemViewSwipeEnabled(): Boolean {
		return false
	}

	override fun isLongPressDragEnabled(): Boolean {
		return true
	}

	override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
		adapter.onRowMoved(viewHolder.adapterPosition, target.adapterPosition)
		return true
	}

	override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
	}

	interface Listener {
		fun onRowMoved(fromPosition: Int, toPosition: Int)
	}
}
