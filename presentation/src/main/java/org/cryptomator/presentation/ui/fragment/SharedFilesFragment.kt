package org.cryptomator.presentation.ui.fragment

import androidx.recyclerview.widget.LinearLayoutManager
import org.cryptomator.generator.Fragment
import org.cryptomator.presentation.databinding.FragmentSharedFilesBinding
import org.cryptomator.presentation.model.CloudFolderModel
import org.cryptomator.presentation.model.SharedFileModel
import org.cryptomator.presentation.model.VaultModel
import org.cryptomator.presentation.model.comparator.VaultPositionComparator
import org.cryptomator.presentation.presenter.SharedFilesPresenter
import org.cryptomator.presentation.ui.adapter.SharedFilesAdapter
import org.cryptomator.presentation.ui.adapter.SharedFilesAdapter.Callback
import org.cryptomator.presentation.ui.adapter.SharedLocationsAdapter
import javax.inject.Inject

@Fragment
class SharedFilesFragment : BaseFragment<FragmentSharedFilesBinding>(FragmentSharedFilesBinding::inflate) {

	@Inject
	lateinit var presenter: SharedFilesPresenter

	@Inject
	lateinit var filesAdapter: SharedFilesAdapter

	@Inject
	lateinit var locationsAdapter: SharedLocationsAdapter

	override fun onResume() {
		super.onResume()
		presenter.displayVaults()
	}

	private val filesAdapterCallback = object : Callback {
		override fun onFileNameConflict(hasFileNameConflict: Boolean) {
			presenter.onFileNameConflict(hasFileNameConflict)
		}
	}

	private val locationsAdapterCallback = object : SharedLocationsAdapter.Callback {
		override fun onVaultSelected(vault: VaultModel?) {
			presenter.onVaultSelected(vault)
		}

		override fun onChooseLocationPressed() {
			presenter.onChooseLocationPressed()
		}
	}

	override fun setupView() {
		binding.receiveSaveLayout.saveFiles.setOnClickListener { presenter.onSaveButtonPressed(filesAdapter.all) }
		setupRecyclerView()
	}

	override fun loadContent() {
		presenter.initialize()
	}

	private fun setupRecyclerView() {
		filesAdapter.setCallback(filesAdapterCallback)
		binding.filesRecyclerView.setHasFixedSize(true)
		binding.filesRecyclerView.layoutManager = LinearLayoutManager(context())
		binding.filesRecyclerView.adapter = filesAdapter

		locationsAdapter.setCallback(locationsAdapterCallback)
		binding.locationsRecyclerView.setHasFixedSize(true)
		binding.locationsRecyclerView.layoutManager = LinearLayoutManager(context())
		binding.locationsRecyclerView.adapter = locationsAdapter
	}

	fun displayVaults(vaults: List<VaultModel>?) {
		val sortedVaults = vaults?.sortedWith(VaultPositionComparator())
		if (sortedVaults?.isNotEmpty() == true) {
			presenter.selectedVault?.let { presenter.selectedVault = sortedVaults[sortedVaults.indexOf(it)] }
			val preselectedVault = presenter.selectedVault ?: sortedVaults[0]
			locationsAdapter.setPreselectedVault(preselectedVault)
			presenter.onVaultSelected(preselectedVault)
		}
		locationsAdapter.clear()
		locationsAdapter.addAll(vaults)
		presenter.location?.let { showChosenLocation(it) }
	}

	fun displayFilesToUpload(files: List<SharedFileModel>?) {
		filesAdapter.show(files)
	}

	fun showChosenLocation(folder: CloudFolderModel) {
		locationsAdapter.setSelectedLocation(if (folder.path.isEmpty()) "/" else folder.path)
	}

}
