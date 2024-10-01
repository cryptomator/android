package org.cryptomator.presentation.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Switch
import org.cryptomator.presentation.databinding.ItemBiometricAuthVaultBinding
import org.cryptomator.presentation.model.VaultModel
import org.cryptomator.presentation.model.comparator.VaultPositionComparator
import org.cryptomator.presentation.ui.adapter.BiometricAuthSettingsAdapter.BiometricAuthSettingsViewHolder
import javax.inject.Inject

class BiometricAuthSettingsAdapter //
@Inject
constructor() : RecyclerViewBaseAdapter<VaultModel, BiometricAuthSettingsAdapter.OnVaultBiometricAuthSettingsChanged, BiometricAuthSettingsViewHolder, ItemBiometricAuthVaultBinding>(VaultPositionComparator()) {

	private var onVaultBiometricAuthSettingsChanged: OnVaultBiometricAuthSettingsChanged? = null

	interface OnVaultBiometricAuthSettingsChanged {

		fun onVaultBiometricAuthSettingsChanged(vaultModel: VaultModel, useBiometricAuth: Boolean)
	}

	fun addOrUpdate(vault: VaultModel?) {
		if (contains(vault)) {
			replaceItem(vault)
		} else {
			addItem(vault)
		}
	}

	override fun getItemBinding(inflater: LayoutInflater, parent: ViewGroup?, viewType: Int): ItemBiometricAuthVaultBinding {
		return ItemBiometricAuthVaultBinding.inflate(inflater, parent, false)
	}

	override fun createViewHolder(binding: ItemBiometricAuthVaultBinding, viewType: Int): BiometricAuthSettingsViewHolder {
		return BiometricAuthSettingsViewHolder(binding)
	}

	fun setOnItemClickListener(onVaultBiometricAuthSettingsChanged: OnVaultBiometricAuthSettingsChanged) {
		this.onVaultBiometricAuthSettingsChanged = onVaultBiometricAuthSettingsChanged
	}

	inner class BiometricAuthSettingsViewHolder(private val binding: ItemBiometricAuthVaultBinding) : RecyclerViewBaseAdapter<*, *, *, *>.ItemViewHolder(binding.root) {

		override fun bind(position: Int) {
			val vaultModel = getItem(position)

			binding.vaultName.text = vaultModel.name
			binding.cloud.setImageResource(vaultModel.cloudType.vaultImageResource)

			binding.toggleBiometricAuth.isChecked = vaultModel.password != null

			//itemView.toggleBiometricAuth.setOnCheckedChangeListener doesn't work because bind can be executed multiple times
			binding.toggleBiometricAuth.setOnClickListener { switch ->
				onVaultBiometricAuthSettingsChanged?.onVaultBiometricAuthSettingsChanged(vaultModel, (switch as Switch).isChecked)
			}
		}
	}
}
