package org.cryptomator.presentation.ui.fragment

import android.util.TypedValue
import androidx.recyclerview.widget.LinearLayoutManager
import org.cryptomator.generator.Fragment
import org.cryptomator.presentation.databinding.FragmentSettingsBiometricAuthBinding
import org.cryptomator.presentation.model.VaultModel
import org.cryptomator.presentation.presenter.BiometricAuthSettingsPresenter
import org.cryptomator.presentation.ui.adapter.BiometricAuthSettingsAdapter
import org.cryptomator.presentation.ui.adapter.BiometricAuthSettingsAdapter.OnVaultBiometricAuthSettingsChanged
import org.cryptomator.util.SharedPreferencesHandler
import javax.inject.Inject

@Fragment
class BiometricAuthSettingsFragment : BaseFragment<FragmentSettingsBiometricAuthBinding>(FragmentSettingsBiometricAuthBinding::inflate) {

	@Inject
	lateinit var adapter: BiometricAuthSettingsAdapter

	@Inject
	lateinit var biometricAuthSettingsPresenter: BiometricAuthSettingsPresenter

	@Inject
	lateinit var sharedPreferencesHandler: SharedPreferencesHandler

	private val onVaultVaultBiometricAuthSettingsChangedListener = object : OnVaultBiometricAuthSettingsChanged {
		override fun onVaultBiometricAuthSettingsChanged(vaultModel: VaultModel, useBiometricAuth: Boolean) {
			biometricAuthSettingsPresenter.updateVaultEntityWithChangedBiometricAuthSettings(vaultModel, useBiometricAuth)
		}
	}

	override fun setupView() {
		setupRecyclerView()
		binding.toggleBiometricAuth.isChecked = sharedPreferencesHandler.useBiometricAuthentication()

		binding.toggleBiometricAuth.setOnCheckedChangeListener { _, isChecked -> biometricAuthSettingsPresenter.switchedGeneralBiometricAuthSettings(isChecked) }

		if (binding.toggleBiometricAuth.isChecked) {
			biometricAuthSettingsPresenter.loadVaultList()
		}
		binding.toggleFaceUnlockConfirmation.isChecked = sharedPreferencesHandler.useConfirmationInFaceUnlockBiometricAuthentication()
		binding.toggleFaceUnlockConfirmation.setOnCheckedChangeListener { _, isChecked -> sharedPreferencesHandler.changeUseConfirmationInFaceUnlockBiometricAuthentication(isChecked) }
	}

	private fun setupRecyclerView() {
		adapter.setOnItemClickListener(onVaultVaultBiometricAuthSettingsChangedListener)
		binding.rvVaults.recyclerView.layoutManager = LinearLayoutManager(context())
		binding.rvVaults.recyclerView.adapter = adapter
		binding.rvVaults.recyclerView.setHasFixedSize(true) // smoother scrolling
		binding.rvVaults.recyclerView.setPadding(0, 0, 0, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 88f, resources.displayMetrics).toInt())
		binding.rvVaults.recyclerView.clipToPadding = false
	}

	fun showVaults(vaultModelCollection: List<VaultModel>?) {
		adapter.clear()

		if (binding.toggleBiometricAuth.isEnabled) {
			adapter.addAll(vaultModelCollection)
		}
	}

	fun addOrUpdateVault(vault: VaultModel?) {
		adapter.addOrUpdate(vault)
	}

	fun clearVaultList() {
		adapter.clear()
	}
}
