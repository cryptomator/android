package org.cryptomator.presentation.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import org.cryptomator.presentation.databinding.ItemCloudBinding
import org.cryptomator.presentation.model.CloudTypeModel
import javax.inject.Inject

class CloudsAdapter @Inject
constructor() : RecyclerViewBaseAdapter<CloudTypeModel, CloudsAdapter.OnItemClickListener, CloudsAdapter.CloudViewHolder, ItemCloudBinding>() {

	interface OnItemClickListener {

		fun onCloudClicked(cloudTypeModel: CloudTypeModel)
	}

	override fun getItemBinding(inflater: LayoutInflater, parent: ViewGroup, viewType: Int): ItemCloudBinding {
		return ItemCloudBinding.inflate(inflater, parent, false)
	}

	override fun createViewHolder(binding: ItemCloudBinding, viewType: Int): CloudViewHolder {
		return CloudViewHolder(binding)
	}

	inner class CloudViewHolder(private val binding: ItemCloudBinding) : RecyclerViewBaseAdapter<CloudTypeModel, CloudsAdapter.OnItemClickListener, CloudsAdapter.CloudViewHolder, ItemCloudBinding>.ItemViewHolder(binding.root) {

		override fun bind(position: Int) {
			val cloudTypeModel = getItem(position)
			binding.cloudImage.setImageResource(cloudTypeModel.cloudImageResource)
			binding.cloudName.setText(cloudTypeModel.displayNameResource)

			binding.root.setOnClickListener { callback.onCloudClicked(cloudTypeModel) }
		}
	}
}
