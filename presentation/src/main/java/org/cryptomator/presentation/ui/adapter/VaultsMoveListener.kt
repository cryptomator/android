package org.cryptomator.presentation.ui.adapter

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

class VaultsMoveListener(val adapter: VaultsAdapter) : ItemTouchHelper.Callback() {

	var dragFrom = -1
	var dragTo = -1

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
		val fromPosition = viewHolder.adapterPosition
		val toPosition = target.adapterPosition

		if (dragFrom == -1) {
			dragFrom = fromPosition;
		}

		dragTo = toPosition;

		adapter.onRowMoved(viewHolder.adapterPosition, target.adapterPosition)
		return true
	}


	override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
		super.clearView(recyclerView, viewHolder)

		if (dragFrom != -1 && dragTo != -1 && dragFrom != dragTo) {
			adapter.onVaultMoved(dragFrom, dragTo)
		}

		dragTo = -1
		dragFrom = -1
	}

	override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
	}

	interface Listener {
		fun onRowMoved(fromPosition: Int, toPosition: Int)
		fun onVaultMoved(fromPosition: Int, toPosition: Int)
	}
}
