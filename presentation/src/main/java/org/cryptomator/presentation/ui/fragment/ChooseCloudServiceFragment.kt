package org.cryptomator.presentation.ui.fragment

import androidx.recyclerview.widget.GridLayoutManager
import kotlinx.android.synthetic.main.recycler_view_layout.*
import org.cryptomator.generator.Fragment
import org.cryptomator.presentation.R
import org.cryptomator.presentation.model.CloudTypeModel
import org.cryptomator.presentation.presenter.ChooseCloudServicePresenter
import org.cryptomator.presentation.ui.adapter.CloudsAdapter
import org.cryptomator.presentation.ui.adapter.CloudsAdapter.OnItemClickListener
import javax.inject.Inject

@Fragment(R.layout.fragment_choose_cloud_service)
class ChooseCloudServiceFragment : BaseFragment() {

	@Inject
	lateinit var chooseCloudServicePresenter: ChooseCloudServicePresenter

	@Inject
	lateinit var cloudsAdapter: CloudsAdapter

	private val onItemClickListener = object : OnItemClickListener {
		override fun onCloudClicked(cloudTypeModel: CloudTypeModel) {
			chooseCloudServicePresenter.cloudPicked(cloudTypeModel)
		}
	}

	override fun setupView() {
		setupRecyclerView()
	}

	fun render(cloudModels: List<CloudTypeModel>?) {
		cloudsAdapter.clear()
		cloudsAdapter.addAll(cloudModels)
	}

	private fun setupRecyclerView() {
		cloudsAdapter.setCallback(onItemClickListener)
		recyclerView.layoutManager = GridLayoutManager(context(), 2)
		recyclerView.adapter = cloudsAdapter
		// smoother scrolling
		recyclerView.setHasFixedSize(true)
	}
}
