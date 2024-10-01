package org.cryptomator.presentation.ui.fragment

import android.util.TypedValue
import android.view.View.GONE
import android.view.View.VISIBLE
import androidx.recyclerview.widget.LinearLayoutManager
import org.cryptomator.generator.Fragment
import org.cryptomator.presentation.databinding.FragmentBrowseCloudConnectionsBinding
import org.cryptomator.presentation.model.CloudModel
import org.cryptomator.presentation.model.CloudTypeModel
import org.cryptomator.presentation.presenter.CloudConnectionListPresenter
import org.cryptomator.presentation.ui.adapter.CloudConnectionListAdapter
import javax.inject.Inject

@Fragment
class CloudConnectionListFragment : BaseFragment<FragmentBrowseCloudConnectionsBinding>(FragmentBrowseCloudConnectionsBinding::inflate) {

	@Inject
	lateinit var cloudConnectionListPresenter: CloudConnectionListPresenter

	@Inject
	lateinit var cloudConnectionListAdapter: CloudConnectionListAdapter

	private var selectedCloudType: CloudTypeModel? = null

	private val onItemClickListener = object : CloudConnectionListAdapter.Callback {
		override fun onCloudSettingsClicked(cloudModel: CloudModel) {
			cloudConnectionListPresenter.onNodeSettingsClicked(cloudModel)
		}

		override fun onCloudConnectionClicked(cloudModel: CloudModel) {
			cloudConnectionListPresenter.onCloudConnectionClicked(cloudModel)
		}
	}

	override fun setupView() {
		setupRecyclerView()
		binding.floatingActionButton.floatingActionButton.setOnClickListener { cloudConnectionListPresenter.onAddConnectionClicked() }
	}

	override fun loadContent() {
		cloudConnectionListPresenter.loadCloudList()
	}

	private fun setupRecyclerView() {
		cloudConnectionListAdapter.setOnItemClickListener(onItemClickListener)
		binding.rvCloudConnections.recyclerView.layoutManager = LinearLayoutManager(context())
		binding.rvCloudConnections.recyclerView.adapter = cloudConnectionListAdapter
		binding.rvCloudConnections.recyclerView.setHasFixedSize(true) // smoother scrolling
		binding.rvCloudConnections.recyclerView.setPadding(0, 0, 0, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 88f, resources.displayMetrics).toInt())
		binding.rvCloudConnections.recyclerView.clipToPadding = false
	}

	fun show(nodes: List<CloudModel>?) {
		cloudConnectionListAdapter.clear()
		cloudConnectionListAdapter.addAll(nodes)
		updateConnectionListHint()
	}

	private fun updateConnectionListHint() {
		binding.rlCreationHint.creationHint.visibility = if (cloudConnectionListAdapter.isEmpty) VISIBLE else GONE
	}

	fun setSelectedCloudType(selectedCloudType: CloudTypeModel) {
		this.selectedCloudType = selectedCloudType
	}
}
