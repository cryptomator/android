package org.cryptomator.presentation.ui.adapter

import android.graphics.drawable.AnimatedVectorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.ItemVaultBinding
import org.cryptomator.presentation.model.VaultModel
import org.cryptomator.presentation.model.comparator.VaultPositionComparator
import org.cryptomator.presentation.ui.adapter.VaultsAdapter.VaultViewHolder
import javax.inject.Inject

enum class VaultUploadState {
	ACTIVE, RESUMABLE
}

class VaultsAdapter @Inject
internal constructor() : RecyclerViewBaseAdapter<VaultModel, VaultsAdapter.OnItemInteractionListener, VaultViewHolder, ItemVaultBinding>(VaultPositionComparator()), VaultsMoveListener.Listener {

	private val uploadStates = mutableMapOf<Long, VaultUploadState>()

	interface OnItemInteractionListener {

		fun onVaultClicked(vaultModel: VaultModel)

		fun onVaultSettingsClicked(vaultModel: VaultModel)

		fun onVaultLockClicked(vaultModel: VaultModel)

		fun onRowMoved(fromPosition: Int, toPosition: Int)

		fun onVaultMoved(fromPosition: Int, toPosition: Int)
	}

	override fun getItemBinding(inflater: LayoutInflater, parent: ViewGroup?, viewType: Int): ItemVaultBinding {
		return ItemVaultBinding.inflate(inflater, parent, false)
	}

	override fun createViewHolder(binding: ItemVaultBinding, viewType: Int): VaultViewHolder {
		return VaultViewHolder(binding)
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

	fun updateUploadState(vaultId: Long, state: VaultUploadState?) {
		if (state != null) {
			uploadStates[vaultId] = state
		} else {
			uploadStates.remove(vaultId)
		}
		val position = itemCollection.indexOfFirst { it.vaultId == vaultId }
		if (position >= 0) {
			notifyItemChanged(position)
		}
	}

	fun setUploadStates(states: Map<Long, VaultUploadState>) {
		uploadStates.clear()
		uploadStates.putAll(states)
		notifyDataSetChanged()
	}

	private fun getVault(vaultId: Long): VaultModel? {
		return itemCollection.firstOrNull { it.vaultId == vaultId }
	}

	inner class VaultViewHolder(private val binding: ItemVaultBinding) : RecyclerViewBaseAdapter<VaultModel, VaultsAdapter.OnItemInteractionListener, VaultViewHolder, ItemVaultBinding>.ItemViewHolder(binding.root) {

		override fun bind(position: Int) {
			val vaultModel = getItem(position)

			binding.vaultName.text = vaultModel.name
			binding.vaultPath.text = vaultModel.path

			binding.cloudImage.setImageResource(vaultModel.cloudType.vaultImageResource)

			if (vaultModel.isLocked) {
				binding.unlockedImage.visibility = View.GONE
			} else {
				binding.unlockedImage.visibility = View.VISIBLE
			}

			(binding.uploadIndicator.drawable as? AnimatedVectorDrawable)?.stop()
			when (uploadStates[vaultModel.vaultId]) {
				VaultUploadState.ACTIVE -> {
					binding.uploadIndicator.setImageResource(R.drawable.ic_upload_arrow_animated)
					binding.uploadIndicator.clearColorFilter()
					binding.uploadIndicator.visibility = View.VISIBLE
					(binding.uploadIndicator.drawable as? AnimatedVectorDrawable)?.start()
				}
				VaultUploadState.RESUMABLE -> {
					binding.uploadIndicator.setImageResource(R.drawable.ic_upload_arrow)
					binding.uploadIndicator.setColorFilter(ContextCompat.getColor(itemView.context, R.color.textColorLight))
					binding.uploadIndicator.visibility = View.VISIBLE
				}
				null -> {
					binding.uploadIndicator.visibility = View.GONE
				}
			}

			itemView.setOnClickListener {
				binding.cloudImage.setImageResource(vaultModel.cloudType.vaultSelectedImageResource)
				callback.onVaultClicked(vaultModel)
			}

			binding.unlockedImage.setOnClickListener { callback.onVaultLockClicked(vaultModel) }

			binding.settings.setOnClickListener {
				binding.cloudImage.setImageResource(vaultModel.cloudType.vaultSelectedImageResource)
				callback.onVaultSettingsClicked(vaultModel)
			}
		}
	}

	override fun onVaultMoved(fromPosition: Int, toPosition: Int) {
		callback.onVaultMoved(fromPosition, toPosition)
	}

	override fun onRowMoved(fromPosition: Int, toPosition: Int) {
		callback.onRowMoved(fromPosition, toPosition)
	}
}
