package org.cryptomator.presentation.ui.adapter

import android.view.View
import org.cryptomator.presentation.R
import org.cryptomator.presentation.model.CloudTypeModel
import org.cryptomator.presentation.ui.adapter.CloudsAdapter.CloudViewHolder
import javax.inject.Inject
import kotlinx.android.synthetic.main.item_cloud.view.cloud
import kotlinx.android.synthetic.main.item_cloud.view.cloudName

class CloudsAdapter @Inject
constructor() : RecyclerViewBaseAdapter<CloudTypeModel, CloudsAdapter.OnItemClickListener, CloudViewHolder>() {

	interface OnItemClickListener {

		fun onCloudClicked(cloudTypeModel: CloudTypeModel)
	}

	override fun getItemLayout(viewType: Int): Int {
		return R.layout.item_cloud
	}

	override fun createViewHolder(view: View, viewType: Int): CloudViewHolder {
		return CloudViewHolder(view)
	}

	inner class CloudViewHolder(itemView: View) : RecyclerViewBaseAdapter<*, *, *>.ItemViewHolder(itemView) {

		override fun bind(position: Int) {
			val cloudTypeModel = getItem(position)
			itemView.cloud.setImageResource(cloudTypeModel.cloudImageLargeResource)
			itemView.cloudName.setText(cloudTypeModel.displayNameResource)

			itemView.cloud.setOnClickListener { callback.onCloudClicked(cloudTypeModel) }
		}
	}
}
