package org.cryptomator.presentation.ui.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.ItemCloudSettingBinding
import org.cryptomator.presentation.model.CloudModel
import org.cryptomator.presentation.model.CloudTypeModel
import org.cryptomator.presentation.ui.adapter.CloudSettingsAdapter.CloudSettingViewHolder
import javax.inject.Inject

class CloudSettingsAdapter @Inject
constructor(private val context: Context) : RecyclerViewBaseAdapter<CloudModel, CloudSettingsAdapter.OnItemClickListener, CloudSettingViewHolder, ItemCloudSettingBinding>() {

	interface OnItemClickListener {

		fun onCloudClicked(cloudModel: CloudModel)
	}

	override fun getItemBinding(inflater: LayoutInflater, parent: ViewGroup?, viewType: Int): ItemCloudSettingBinding {
		return ItemCloudSettingBinding.inflate(inflater, parent, false)
	}

	override fun createViewHolder(binding: ItemCloudSettingBinding, viewType: Int): CloudSettingViewHolder {
		return CloudSettingViewHolder(binding)
	}

	fun notifyCloudChanged(changedCloud: CloudModel?) {
		val position = positionOf(changedCloud)
		if (position != -1) {
			replaceItem(position, changedCloud)
		}
	}

	inner class CloudSettingViewHolder(private val binding: ItemCloudSettingBinding) : RecyclerViewBaseAdapter<CloudModel, CloudSettingsAdapter.OnItemClickListener, CloudSettingViewHolder, ItemCloudSettingBinding>.ItemViewHolder(binding.root) {

		override fun bind(position: Int) {
			val cloudModel = getItem(position)

			binding.cloudImage.setImageResource(cloudModel.cloudType().cloudImageResource)

			when (cloudModel.cloudType()) {
				CloudTypeModel.ONEDRIVE -> binding.cloudName.text = context.getString(R.string.screen_cloud_settings_onedrive_connections)
				CloudTypeModel.PCLOUD -> binding.cloudName.text = context.getString(R.string.screen_cloud_settings_pcloud_connections)
				CloudTypeModel.S3 -> binding.cloudName.text = context.getString(R.string.screen_cloud_settings_s3_connections)
				CloudTypeModel.WEBDAV -> binding.cloudName.text = context.getString(R.string.screen_cloud_settings_webdav_connections)
				CloudTypeModel.LOCAL -> binding.cloudName.text = context.getString(R.string.screen_cloud_settings_local_storage_locations)
				else -> {
					binding.cloudName.text = getCloudNameText(isAlreadyLoggedIn(cloudModel), cloudModel)
					if (isAlreadyLoggedIn(cloudModel)) {
						binding.cloudUsername.text = cloudModel.username()
						binding.cloudUsername.visibility = View.VISIBLE
					} else {
						binding.cloudUsername.visibility = View.GONE
					}
				}
			}

			itemView.setOnClickListener { this@CloudSettingsAdapter.callback.onCloudClicked(cloudModel) }
		}

		private fun isAlreadyLoggedIn(cloudModel: CloudModel): Boolean {
			return cloudModel.username() != null
		}

		private fun getCloudNameText(alreadyLoggedIn: Boolean, cloudModel: CloudModel): String {
			return getCloudStatusText(alreadyLoggedIn) + " " + context.getString(cloudModel.name())
		}

		private fun getCloudStatusText(alreadyLoggedIn: Boolean): String {
			return if (alreadyLoggedIn)
				context.getString(R.string.screen_cloud_settings_sign_out_from_cloud) //
			else
				context.getString(R.string.screen_cloud_settings_log_in_to)
		}
	}
}
