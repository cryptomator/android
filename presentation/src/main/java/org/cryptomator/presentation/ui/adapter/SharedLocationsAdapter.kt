package org.cryptomator.presentation.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.cryptomator.presentation.databinding.ItemShareableLocationBinding
import org.cryptomator.presentation.model.VaultModel
import org.cryptomator.presentation.model.comparator.VaultPositionComparator
import org.cryptomator.presentation.ui.adapter.SharedLocationsAdapter.VaultViewHolder
import javax.inject.Inject

class SharedLocationsAdapter @Inject
constructor() : RecyclerViewBaseAdapter<VaultModel, SharedLocationsAdapter.Callback, VaultViewHolder, ItemShareableLocationBinding>(VaultPositionComparator()) {

	private var selectedVault: VaultModel? = null
	private var selectedLocation: String? = null

	interface Callback {

		fun onVaultSelected(vault: VaultModel?)

		fun onChooseLocationPressed()
	}

	fun setPreselectedVault(preselectedVault: VaultModel) {
		this.selectedVault = preselectedVault
		this.selectedLocation = null
	}

	fun setPreselectedLocation(preselectedLocation: String) {
		this.selectedLocation = preselectedLocation
	}

	fun setSelectedLocation(selectedLocation: String) {
		this.selectedLocation = selectedLocation
		replaceItem(this.selectedVault)
	}

	override fun getItemBinding(inflater: LayoutInflater, parent: ViewGroup?, viewType: Int): ItemShareableLocationBinding {
		return ItemShareableLocationBinding.inflate(inflater, parent, false)
	}

	override fun createViewHolder(binding: ItemShareableLocationBinding, viewType: Int): VaultViewHolder {
		return VaultViewHolder(binding)
	}

	private fun selectVault(selectedVault: VaultModel?) {
		this.selectedLocation = null
		if (this.selectedVault != null) {
			replaceItem(this.selectedVault)
		}
		this.selectedVault = selectedVault
		replaceItem(this.selectedVault)
		callback.onVaultSelected(this.selectedVault)
	}

	inner class VaultViewHolder(private val binding: ItemShareableLocationBinding) : RecyclerViewBaseAdapter<*, *, *, *>.ItemViewHolder(binding.root) {

		private var boundVault: VaultModel? = null

		override fun bind(position: Int) {
			removeListener()

			boundVault = getItem(position)

			boundVault?.let {
				binding.vaultName.text = it.name

				val boundVaultSelected = it == selectedVault
				binding.selectedVault.isChecked = boundVaultSelected
				binding.selectedVault.isClickable = !boundVaultSelected
				if (boundVaultSelected) {
					binding.cloudImage.setImageResource(it.cloudType.vaultSelectedImageResource)
					if (selectedLocation != null) {
						binding.chosenLocation.visibility = View.VISIBLE
						binding.chosenLocation.text = selectedLocation
					} else {
						binding.chosenLocation.visibility = View.GONE
					}
					binding.chooseFolderLocation.visibility = View.VISIBLE
				} else {
					binding.cloudImage.setImageResource(it.cloudType.vaultImageResource)
					binding.chosenLocation.visibility = View.GONE
					binding.chooseFolderLocation.visibility = View.GONE
				}
			}

			bindListener()
		}

		private fun removeListener() {
			itemView.setOnClickListener(null)
			binding.selectedVault.setOnCheckedChangeListener(null)
		}

		private fun bindListener() {
			itemView.setOnClickListener {
				if (!binding.selectedVault.isChecked) {
					selectVault(boundVault)
				}
			}
			binding.selectedVault.setOnCheckedChangeListener { _, isChecked ->
				if (isChecked) {
					selectVault(boundVault)
				}
			}
			binding.chooseFolderLocation.setOnClickListener { callback.onChooseLocationPressed() }
		}
	}
}
