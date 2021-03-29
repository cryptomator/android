package org.cryptomator.presentation.ui.adapter

import android.view.View
import com.google.android.material.switchmaterial.SwitchMaterial
import org.cryptomator.presentation.R
import org.cryptomator.presentation.model.VaultModel
import org.cryptomator.presentation.model.comparator.VaultPositionComparator
import org.cryptomator.presentation.ui.adapter.BiometricAuthSettingsAdapter.BiometricAuthSettingsViewHolder
import javax.inject.Inject
import kotlinx.android.synthetic.main.item_biometric_auth_vault.view.cloud
import kotlinx.android.synthetic.main.item_biometric_auth_vault.view.toggleBiometricAuth
import kotlinx.android.synthetic.main.item_biometric_auth_vault.view.vaultName

class BiometricAuthSettingsAdapter //
@Inject
constructor() : RecyclerViewBaseAdapter<VaultModel, BiometricAuthSettingsAdapter.OnVaultBiometricAuthSettingsChanged, BiometricAuthSettingsViewHolder>(VaultPositionComparator()) {

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

	override fun getItemLayout(viewType: Int): Int {
		return R.layout.item_biometric_auth_vault
	}

	override fun createViewHolder(view: View, viewType: Int): BiometricAuthSettingsViewHolder {
		return BiometricAuthSettingsViewHolder(view)
	}

	fun setOnItemClickListener(onVaultBiometricAuthSettingsChanged: OnVaultBiometricAuthSettingsChanged) {
		this.onVaultBiometricAuthSettingsChanged = onVaultBiometricAuthSettingsChanged
	}

	inner class BiometricAuthSettingsViewHolder(itemView: View) : RecyclerViewBaseAdapter<*, *, *>.ItemViewHolder(itemView) {

		override fun bind(position: Int) {
			val vaultModel = getItem(position)

			itemView.vaultName.text = vaultModel.name
			itemView.cloud.setImageResource(vaultModel.cloudType.vaultImageResource)

			itemView.toggleBiometricAuth.isChecked = vaultModel.password != null

			//itemView.toggleBiometricAuth.setOnCheckedChangeListener doesn't work because bind can be executed multiple times
			itemView.toggleBiometricAuth.setOnClickListener { switch ->
				onVaultBiometricAuthSettingsChanged?.onVaultBiometricAuthSettingsChanged(vaultModel, (switch as SwitchMaterial).isChecked)
			}
		}
	}
}
