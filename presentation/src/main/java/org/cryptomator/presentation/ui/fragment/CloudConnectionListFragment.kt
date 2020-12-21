package org.cryptomator.presentation.ui.fragment

import android.os.Environment
import android.util.TypedValue
import android.view.View.GONE
import android.view.View.VISIBLE
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.fragment_browse_cloud_connections.*
import kotlinx.android.synthetic.main.recycler_view_layout.*
import kotlinx.android.synthetic.main.view_cloud_connection_content.*
import kotlinx.android.synthetic.main.view_empty_cloud_connections.*
import org.cryptomator.generator.Fragment
import org.cryptomator.presentation.R
import org.cryptomator.presentation.model.CloudModel
import org.cryptomator.presentation.model.CloudTypeModel
import org.cryptomator.presentation.presenter.CloudConnectionListPresenter
import org.cryptomator.presentation.ui.adapter.CloudConnectionListAdapter
import javax.inject.Inject

@Fragment(R.layout.fragment_browse_cloud_connections)
class CloudConnectionListFragment : BaseFragment() {

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
		rv_local_default_cloud.setOnClickListener { cloudConnectionListPresenter.onDefaultLocalCloudConnectionClicked() }
		floating_action_button.setOnClickListener { cloudConnectionListPresenter.onAddConnectionClicked() }
		emptyCloudConnectionsHint.setText(R.string.screen_cloud_connections_no_connections)
	}

	override fun loadContent() {
		cloudConnectionListPresenter.loadCloudList()
	}

	private fun setupRecyclerView() {
		cloudConnectionListAdapter.setOnItemClickListener(onItemClickListener)
		recyclerView.layoutManager = LinearLayoutManager(context())
		recyclerView.adapter = cloudConnectionListAdapter
		recyclerView.setHasFixedSize(true) // smoother scrolling
		recyclerView.setPadding(0, 0, 0, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 88f, resources.displayMetrics).toInt())
		recyclerView.clipToPadding = false
	}

	fun show(nodes: List<CloudModel>?) {
		cloudConnectionListAdapter.clear()
		cloudConnectionListAdapter.addAll(nodes)
		updateConnectionListHint()
	}

	private fun updateConnectionListHint() {
		rl_empty_cloud_connections_hint.visibility = if (cloudConnectionListAdapter.isEmpty) VISIBLE else GONE
	}

	fun setSelectedCloudType(selectedCloudType: CloudTypeModel) {
		this.selectedCloudType = selectedCloudType

		if (CloudTypeModel.LOCAL == selectedCloudType) {
			rv_local_default_cloud.visibility = VISIBLE
			cloudText.text = getString(R.string.screen_cloud_local_default_storage_title)
			cloudSubText.text = Environment.getExternalStorageDirectory().toString()
		}
	}
}
