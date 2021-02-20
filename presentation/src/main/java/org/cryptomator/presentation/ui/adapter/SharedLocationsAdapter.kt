package org.cryptomator.presentation.ui.adapter

import android.view.View
import org.cryptomator.presentation.R
import org.cryptomator.presentation.model.VaultModel
import org.cryptomator.presentation.model.comparator.VaultPositionComparator
import org.cryptomator.presentation.ui.adapter.SharedLocationsAdapter.VaultViewHolder
import javax.inject.Inject
import kotlinx.android.synthetic.main.item_shareable_location.view.chooseFolderLocation
import kotlinx.android.synthetic.main.item_shareable_location.view.chosenLocation
import kotlinx.android.synthetic.main.item_shareable_location.view.cloudImage
import kotlinx.android.synthetic.main.item_shareable_location.view.selectedVault
import kotlinx.android.synthetic.main.item_shareable_location.view.vaultName

class SharedLocationsAdapter @Inject
constructor() : RecyclerViewBaseAdapter<VaultModel, SharedLocationsAdapter.Callback, VaultViewHolder>(VaultPositionComparator()) {

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

	override fun getItemLayout(viewType: Int): Int {
		return R.layout.item_shareable_location
	}

	override fun createViewHolder(view: View, viewType: Int): VaultViewHolder {
		return VaultViewHolder(view)
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

	inner class VaultViewHolder(itemView: View) : RecyclerViewBaseAdapter<*, *, *>.ItemViewHolder(itemView) {

		private var boundVault: VaultModel? = null

		override fun bind(position: Int) {
			removeListener()

			boundVault = getItem(position)

			boundVault?.let {
				itemView.cloudImage.setImageResource(it.cloudType.cloudImageResource)
				itemView.vaultName.text = it.name

				val boundVaultSelected = it == selectedVault
				itemView.selectedVault.isChecked = boundVaultSelected
				itemView.selectedVault.isClickable = !boundVaultSelected
				if (boundVaultSelected) {
					if (selectedLocation != null) {
						itemView.chosenLocation.visibility = View.VISIBLE
						itemView.chosenLocation.text = selectedLocation
					} else {
						itemView.chosenLocation.visibility = View.GONE
					}
					itemView.chooseFolderLocation.visibility = View.VISIBLE
				} else {
					itemView.chosenLocation.visibility = View.GONE
					itemView.chooseFolderLocation.visibility = View.GONE
				}
			}

			bindListener()
		}

		private fun removeListener() {
			itemView.setOnClickListener(null)
			itemView.selectedVault.setOnCheckedChangeListener(null)
		}

		private fun bindListener() {
			itemView.setOnClickListener {
				if (!itemView.selectedVault.isChecked) {
					selectVault(boundVault)
				}
			}
			itemView.selectedVault.setOnCheckedChangeListener { _, isChecked ->
				if (isChecked) {
					selectVault(boundVault)
				}
			}
			itemView.chooseFolderLocation.setOnClickListener { callback.onChooseLocationPressed() }
		}
	}
}
