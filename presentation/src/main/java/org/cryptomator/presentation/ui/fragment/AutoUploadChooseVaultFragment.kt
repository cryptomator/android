package org.cryptomator.presentation.ui.fragment

import androidx.recyclerview.widget.LinearLayoutManager
import org.cryptomator.generator.Fragment
import org.cryptomator.presentation.databinding.FragmentAutoUploadChooseVaultBinding
import org.cryptomator.presentation.model.CloudFolderModel
import org.cryptomator.presentation.model.VaultModel
import org.cryptomator.presentation.presenter.AutoUploadChooseVaultPresenter
import org.cryptomator.presentation.ui.adapter.SharedLocationsAdapter
import org.cryptomator.util.SharedPreferencesHandler
import javax.inject.Inject

@Fragment
class AutoUploadChooseVaultFragment : BaseFragment<FragmentAutoUploadChooseVaultBinding>(FragmentAutoUploadChooseVaultBinding::inflate) {

	@Inject
	lateinit var presenter: AutoUploadChooseVaultPresenter

	@Inject
	lateinit var locationsAdapter: SharedLocationsAdapter

	@Inject
	lateinit var sharedPreferencesHandler: SharedPreferencesHandler

	override fun setupView() {
		binding.toolbarBottom.saveFiles.setOnClickListener { presenter.onChooseVaultPressed() }

		setupRecyclerView()
	}

	override fun loadContent() {
		presenter.displayVaults()
	}

	private val locationsAdapterCallback = object : SharedLocationsAdapter.Callback {
		override fun onVaultSelected(vault: VaultModel?) {
			presenter.onVaultSelected(vault)
		}

		override fun onChooseLocationPressed() {
			presenter.onChooseLocationPressed()
		}
	}

	fun displayVaults(vaults: List<VaultModel>) {
		locationsAdapter.clear()

		if (vaults.isNotEmpty()) {
			var preselectedVault = vaults[0]

			val photoUploadVaultId = SharedPreferencesHandler(context()).photoUploadVault()
			vaults.forEach { vaultModel ->
				if (vaultModel.vaultId == photoUploadVaultId) {
					preselectedVault = vaultModel
					return@forEach
				}
			}

			locationsAdapter.setPreselectedVault(preselectedVault)
			locationsAdapter.setPreselectedLocation(sharedPreferencesHandler.photoUploadVaultFolder())
			presenter.onVaultSelected(preselectedVault)
		}
		locationsAdapter.addAll(vaults)
	}

	private fun setupRecyclerView() {
		locationsAdapter.setCallback(locationsAdapterCallback)
		binding.locationsRecyclerView.setHasFixedSize(true)
		binding.locationsRecyclerView.layoutManager = LinearLayoutManager(context())
		binding.locationsRecyclerView.adapter = locationsAdapter
	}

	fun showChosenLocation(folder: CloudFolderModel) {
		locationsAdapter.setSelectedLocation(if (folder.path.isEmpty()) "/" else folder.path)
	}
}
