package org.cryptomator.presentation.ui.adapter

import android.view.View
import kotlinx.android.synthetic.main.item_vault.view.*
import org.cryptomator.presentation.R
import org.cryptomator.presentation.model.VaultModel
import org.cryptomator.presentation.ui.adapter.VaultsAdapter.VaultViewHolder
import javax.inject.Inject

class VaultsAdapter @Inject
internal constructor() : RecyclerViewBaseAdapter<VaultModel, VaultsAdapter.OnItemInteractionListener, VaultViewHolder>(VaultModelComparator()), VaultsMoveListener.Listener {
	interface OnItemInteractionListener {
		fun onVaultClicked(vaultModel: VaultModel)

		fun onVaultSettingsClicked(vaultModel: VaultModel)

		fun onVaultLockClicked(vaultModel: VaultModel)

		fun onRowMoved(fromPosition: Int, toPosition: Int)

		fun onVaultMoved(fromPosition: Int, toPosition: Int)
	}

	override fun getItemLayout(viewType: Int): Int {
		return R.layout.item_vault
	}

	override fun createViewHolder(view: View, viewType: Int): VaultViewHolder {
		return VaultViewHolder(view)
	}

	fun deleteVault(vaultID: Long) {
		deleteItem(getVault(vaultID))
	}

	fun addOrUpdateVault(vault: VaultModel?) {
		if (contains(vault)) {
			replaceItem(vault)
		} else {
			addItem(vault)
		}
	}

	private fun getVault(vaultId: Long): VaultModel? {
		return itemCollection.firstOrNull { it.vaultId == vaultId }
	}

	inner class VaultViewHolder(itemView: View) : RecyclerViewBaseAdapter<*, *, *>.ItemViewHolder(itemView) {

		override fun bind(position: Int) {
			val vaultModel = getItem(position)

			itemView.vaultName.text = vaultModel.name
			itemView.vaultPath.text = vaultModel.path

			itemView.cloudImage.setImageResource(vaultModel.cloudType.cloudImageResource)

			if (vaultModel.isLocked) {
				itemView.unlockedImage.visibility = View.GONE
			} else {
				itemView.unlockedImage.visibility = View.VISIBLE
			}

			itemView.setOnClickListener { callback.onVaultClicked(vaultModel) }

			itemView.unlockedImage.setOnClickListener { callback.onVaultLockClicked(vaultModel) }

			itemView.settings.setOnClickListener { callback.onVaultSettingsClicked(vaultModel) }
		}
	}

	override fun onVaultMoved(fromPosition: Int, toPosition: Int) {
		callback.onVaultMoved(fromPosition, toPosition)
	}

	override fun onRowMoved(fromPosition: Int, toPosition: Int) {
		callback.onRowMoved(fromPosition, toPosition)
	}

	internal class VaultModelComparator : java.util.Comparator<VaultModel> {
		override fun compare(o1: VaultModel, o2: VaultModel): Int {
			return o1.position - o2.position
		}
	}
}
