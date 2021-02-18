package org.cryptomator.presentation.ui.fragment

import android.util.TypedValue
import android.view.View
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import org.cryptomator.generator.Fragment
import org.cryptomator.presentation.R
import org.cryptomator.presentation.model.VaultModel
import org.cryptomator.presentation.presenter.VaultListPresenter
import org.cryptomator.presentation.ui.adapter.VaultsAdapter
import org.cryptomator.presentation.ui.adapter.VaultsMoveListener
import javax.inject.Inject
import kotlinx.android.synthetic.main.fragment_vault_list.coordinatorLayout
import kotlinx.android.synthetic.main.fragment_vault_list.floating_action_button
import kotlinx.android.synthetic.main.recycler_view_layout.recyclerView
import kotlinx.android.synthetic.main.view_vault_creation_hint.rl_creation_hint

@Fragment(R.layout.fragment_vault_list)
class VaultListFragment : BaseFragment() {

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
		floating_action_button.setOnClickListener { vaultListPresenter.onCreateVaultClicked() }
	}

	override fun onResume() {
		super.onResume()
		vaultListPresenter.loadVaultList()
	}

	private fun setupRecyclerView() {
		vaultsAdapter.setCallback(onItemClickListener)
		touchHelper = ItemTouchHelper(VaultsMoveListener(vaultsAdapter))
		touchHelper.attachToRecyclerView(recyclerView)

		recyclerView.layoutManager = LinearLayoutManager(context())
		recyclerView.adapter = vaultsAdapter
		recyclerView.setHasFixedSize(true) // smoother scrolling
		recyclerView.setPadding(0, 0, 0, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 88f, resources.displayMetrics).toInt())
		recyclerView.clipToPadding = false
	}

	fun showVaults(vaultModelCollection: List<VaultModel>?) {
		vaultsAdapter.clear()
		vaultsAdapter.addAll(vaultModelCollection)
	}

	fun showVaultCreationHint() {
		rl_creation_hint.visibility = View.VISIBLE
	}

	fun hideVaultCreationHint() {
		rl_creation_hint.visibility = View.GONE
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

	fun rootView(): View = coordinatorLayout
}
