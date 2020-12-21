package org.cryptomator.presentation.ui.fragment

import android.util.TypedValue
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.fragment_settings_biometric_auth.*
import kotlinx.android.synthetic.main.recycler_view_layout.*
import org.cryptomator.generator.Fragment
import org.cryptomator.presentation.R
import org.cryptomator.presentation.model.VaultModel
import org.cryptomator.presentation.presenter.BiometricAuthSettingsPresenter
import org.cryptomator.presentation.ui.adapter.BiometricAuthSettingsAdapter
import org.cryptomator.presentation.ui.adapter.BiometricAuthSettingsAdapter.OnVaultBiometricAuthSettingsChanged
import org.cryptomator.util.SharedPreferencesHandler
import javax.inject.Inject

@Fragment(R.layout.fragment_settings_biometric_auth)
class BiometricAuthSettingsFragment : BaseFragment() {

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

		toggleBiometricAuth.isChecked = sharedPreferencesHandler.useBiometricAuthentication()

		toggleBiometricAuth.setOnCheckedChangeListener { _, isChecked -> biometricAuthSettingsPresenter.switchedGeneralBiometricAuthSettings(isChecked) }

		if (toggleBiometricAuth.isChecked) {
			biometricAuthSettingsPresenter.loadVaultList()
		}

		toggleFaceUnlockConfirmation.isChecked = sharedPreferencesHandler.useConfirmationInFaceUnlockBiometricAuthentication()
		toggleFaceUnlockConfirmation.setOnCheckedChangeListener { _, isChecked -> sharedPreferencesHandler.changeUseConfirmationInFaceUnlockBiometricAuthentication(isChecked) }
	}

	private fun setupRecyclerView() {
		adapter.setOnItemClickListener(onVaultVaultBiometricAuthSettingsChangedListener)
		recyclerView.layoutManager = LinearLayoutManager(context())
		recyclerView.adapter = adapter
		recyclerView.setHasFixedSize(true) // smoother scrolling
		recyclerView.setPadding(0, 0, 0, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 88f, resources.displayMetrics).toInt())
		recyclerView.clipToPadding = false
	}

	fun showVaults(vaultModelCollection: List<VaultModel>?) {
		adapter.clear()

		if (toggleBiometricAuth.isEnabled) {
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
