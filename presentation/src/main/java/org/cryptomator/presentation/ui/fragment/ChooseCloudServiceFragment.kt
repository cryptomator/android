package org.cryptomator.presentation.ui.fragment

import androidx.recyclerview.widget.LinearLayoutManager
import org.cryptomator.generator.Fragment
import org.cryptomator.presentation.R
import org.cryptomator.presentation.model.CloudTypeModel
import org.cryptomator.presentation.presenter.ChooseCloudServicePresenter
import org.cryptomator.presentation.ui.adapter.CloudsAdapter
import org.cryptomator.presentation.ui.adapter.CloudsAdapter.OnItemClickListener
import javax.inject.Inject
import kotlinx.android.synthetic.main.recycler_view_layout.recyclerView

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
		chooseCloudServicePresenter.showCloudMissingSnackbarHintInLiteVariant()
	}

	fun render(cloudModels: List<CloudTypeModel>?) {
		cloudsAdapter.clear()
		cloudsAdapter.addAll(cloudModels)
	}

	private fun setupRecyclerView() {
		cloudsAdapter.setCallback(onItemClickListener)
		recyclerView.layoutManager = LinearLayoutManager(context())
		recyclerView.adapter = cloudsAdapter
		// smoother scrolling
		recyclerView.setHasFixedSize(true)
	}
}
