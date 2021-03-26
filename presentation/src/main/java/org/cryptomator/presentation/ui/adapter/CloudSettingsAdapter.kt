package org.cryptomator.presentation.ui.adapter

import android.content.Context
import android.view.View
import org.cryptomator.presentation.R
import org.cryptomator.presentation.model.CloudModel
import org.cryptomator.presentation.model.CloudTypeModel
import org.cryptomator.presentation.ui.adapter.CloudSettingsAdapter.CloudSettingViewHolder
import javax.inject.Inject
import kotlinx.android.synthetic.main.item_cloud_setting.view.*

class CloudSettingsAdapter @Inject
constructor(private val context: Context) : RecyclerViewBaseAdapter<CloudModel, CloudSettingsAdapter.OnItemClickListener, CloudSettingViewHolder>() {

	interface OnItemClickListener {

		fun onCloudClicked(cloudModel: CloudModel)
	}

	override fun getItemLayout(viewType: Int): Int {
		return R.layout.item_cloud_setting
	}

	override fun createViewHolder(view: View, viewType: Int): CloudSettingViewHolder {
		return CloudSettingViewHolder(view)
	}

	fun notifyCloudChanged(changedCloud: CloudModel?) {
		val position = positionOf(changedCloud)
		if (position != -1) {
			replaceItem(position, changedCloud)
		}
	}

	inner class CloudSettingViewHolder(itemView: View) : RecyclerViewBaseAdapter<*, *, *>.ItemViewHolder(itemView) {

		override fun bind(position: Int) {
			val cloudModel = getItem(position)

			itemView.cloudImage.setImageResource(cloudModel.cloudType().cloudImageResource)

			if (webdav(cloudModel.cloudType())) {
				itemView.cloudName.text = context.getString(R.string.screen_cloud_settings_webdav_connections)
			} else if (pCloud(cloudModel.cloudType())) {
				itemView.cloudName.text = context.getString(R.string.screen_cloud_settings_pcloud_connections)
			} else if (local(cloudModel.cloudType())) {
				itemView.cloudName.text = context.getString(R.string.screen_cloud_settings_local_storage_locations)
			} else {
				itemView.cloudName.text = getCloudNameText(isAlreadyLoggedIn(cloudModel), cloudModel)
				if (isAlreadyLoggedIn(cloudModel)) {
					itemView.cloudUsername.text = cloudModel.username()
					itemView.cloudUsername.visibility = View.VISIBLE
				} else {
					itemView.cloudUsername.visibility = View.GONE
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

	private fun local(cloudType: CloudTypeModel): Boolean {
		return CloudTypeModel.LOCAL == cloudType
	}

	private fun webdav(cloudType: CloudTypeModel): Boolean {
		return CloudTypeModel.WEBDAV == cloudType
	}

	private fun pCloud(cloudType: CloudTypeModel): Boolean {
		return CloudTypeModel.PCLOUD == cloudType
	}
}
