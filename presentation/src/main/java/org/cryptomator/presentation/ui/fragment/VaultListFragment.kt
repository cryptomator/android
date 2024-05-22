package org.cryptomator.presentation.ui.fragment

import android.util.TypedValue
import android.view.View
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import org.cryptomator.generator.Fragment
import org.cryptomator.presentation.databinding.FragmentVaultListBinding
import org.cryptomator.presentation.model.VaultModel
import org.cryptomator.presentation.presenter.VaultListPresenter
import org.cryptomator.presentation.ui.adapter.VaultsAdapter
import org.cryptomator.presentation.ui.adapter.VaultsMoveListener
import javax.inject.Inject

@Fragment
class VaultListFragment : BaseFragment<FragmentVaultListBinding>(FragmentVaultListBinding::inflate) {

	@Inject
	lateinit var vaultListPresenter: VaultListPresenter

	@Inject
	lateinit var vaultsAdapter: VaultsAdapter

	lateinit var touchHelper: ItemTouchHelper

	private val onItemClickListener = object : VaultsAdapter.OnItemInteractionListener {
		override fun onVaultClicked(vaultModel: VaultModel) {
			vaultListPresenter.onVaultClicked(vaultModel)
		}

		override fun onVaultSettingsClicked(vaultModel: VaultModel) {
			vaultListPresenter.onVaultSettingsClicked(vaultModel)
		}

		override fun onVaultLockClicked(vaultModel: VaultModel) {
			vaultListPresenter.onVaultLockClicked(vaultModel)
		}

		override fun onRowMoved(fromPosition: Int, toPosition: Int) {
			vaultListPresenter.onRowMoved(fromPosition, toPosition)
		}

		override fun onVaultMoved(fromPosition: Int, toPosition: Int) {
			vaultListPresenter.onVaultMoved(fromPosition, toPosition)
		}
	}


	override fun setupView() {
		setupRecyclerView()
		binding.floatingActionButton.floatingActionButton.setOnClickListener { vaultListPresenter.onCreateVaultClicked() }
	}

	override fun onResume() {
		super.onResume()
		vaultListPresenter.loadVaultList()
	}

	private fun setupRecyclerView() {
		vaultsAdapter.setCallback(onItemClickListener)
		touchHelper = ItemTouchHelper(VaultsMoveListener(vaultsAdapter))
		touchHelper.attachToRecyclerView(binding.rvVaults.recyclerView)

		binding.rvVaults.recyclerView.layoutManager = LinearLayoutManager(context())
		binding.rvVaults.recyclerView.adapter = vaultsAdapter
		binding.rvVaults.recyclerView.setHasFixedSize(true) // smoother scrolling
		binding.rvVaults.recyclerView.setPadding(0, 0, 0, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 88f, resources.displayMetrics).toInt())
		binding.rvVaults.recyclerView.clipToPadding = false
	}

	fun showVaults(vaultModelCollection: List<VaultModel>?) {
		vaultsAdapter.clear()
		vaultsAdapter.addAll(vaultModelCollection)
	}

	fun showVaultCreationHint() {
		binding.rlCreationHint.creationHint.visibility = View.VISIBLE
	}

	fun hideVaultCreationHint() {
		binding.rlCreationHint.creationHint.visibility = View.GONE
	}

	fun isVaultLocked(vaultModel: VaultModel?): Boolean {
		return vaultsAdapter.getItem(vaultsAdapter.positionOf(vaultModel)).isLocked
	}

	fun deleteVaultFromAdapter(vaultId: Long) {
		vaultsAdapter.deleteVault(vaultId)
		if (vaultsAdapter.isEmpty) {
			showVaultCreationHint()
		}
	}

	fun addOrUpdateVault(vaultModel: VaultModel?) {
		vaultsAdapter.addOrUpdateVault(vaultModel)
	}

	fun vaultMoved(vaults: List<VaultModel>) {
		vaultsAdapter.clear()
		vaultsAdapter.addAll(vaults)
	}

	fun rowMoved(fromPosition: Int, toPosition: Int) {
		vaultsAdapter.notifyItemMoved(fromPosition, toPosition)
	}

	fun rootView(): View = binding.coordinatorLayout
}
