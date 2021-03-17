package org.cryptomator.presentation.ui.adapter

import android.content.Context
import android.net.Uri
import android.view.View
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.presentation.R
import org.cryptomator.presentation.model.CloudModel
import org.cryptomator.presentation.model.LocalStorageModel
import org.cryptomator.presentation.model.PCloudModel
import org.cryptomator.presentation.model.WebDavCloudModel
import org.cryptomator.presentation.model.comparator.CloudModelComparator
import org.cryptomator.presentation.ui.adapter.CloudConnectionListAdapter.CloudConnectionHolder
import java.net.URISyntaxException
import javax.inject.Inject
import kotlinx.android.synthetic.main.item_browse_cloud_model_connections.view.cloudImage
import kotlinx.android.synthetic.main.item_browse_cloud_model_connections.view.settings
import kotlinx.android.synthetic.main.view_cloud_connection_content.view.cloudSubText
import kotlinx.android.synthetic.main.view_cloud_connection_content.view.cloudText

class CloudConnectionListAdapter @Inject
internal constructor(context: Context) : RecyclerViewBaseAdapter<CloudModel, CloudConnectionListAdapter.Callback, CloudConnectionHolder>(CloudModelComparator(context)) {

	interface Callback {

		fun onCloudConnectionClicked(cloudModel: CloudModel)

		fun onCloudSettingsClicked(cloudModel: CloudModel)
	}

	override fun getItemLayout(viewType: Int): Int {
		return R.layout.item_browse_cloud_model_connections
	}

	override fun createViewHolder(view: View, viewType: Int): CloudConnectionHolder {
		return CloudConnectionHolder(view)
	}

	fun setOnItemClickListener(callback: Callback) {
		this.callback = callback
	}

	inner class CloudConnectionHolder(itemView: View) : RecyclerViewBaseAdapter<*, *, *>.ItemViewHolder(itemView) {

		override fun bind(position: Int) {
			internalBind(getItem(position))
		}

		private fun internalBind(cloudModel: CloudModel) {
			itemView.settings.setOnClickListener { callback.onCloudSettingsClicked(cloudModel) }

			itemView.cloudImage.setImageResource(cloudModel.cloudType().cloudImageResource)

			itemView.setOnClickListener { callback.onCloudConnectionClicked(cloudModel) }

			if (cloudModel is WebDavCloudModel) {
				bindWebDavCloudModel(cloudModel)
			} else if (cloudModel is PCloudModel) {
				bindPCloudModel(cloudModel)
			} else if (cloudModel is LocalStorageModel) {
				bindLocalStorageCloudModel(cloudModel)
			}
		}

		private fun bindWebDavCloudModel(cloudModel: WebDavCloudModel) {
			try {
				val uri = Uri.parse(cloudModel.url())
				itemView.cloudText.text = uri.authority
				itemView.cloudSubText.text = String.format("%s • %s", cloudModel.username(), uri.path)
			} catch (e: URISyntaxException) {
				throw FatalBackendException("path in WebDAV cloud isn't correct (no uri)")
			}

		}

		private fun bindPCloudModel(cloudModel: PCloudModel) {
				itemView.cloudText.text = cloudModel.username()
				itemView.cloudSubText.visibility = View.GONE
		}

		private fun bindLocalStorageCloudModel(cloudModel: LocalStorageModel) {
			if (cloudModel.location().isEmpty()) {
				itemView.cloudText.text = cloudModel.storage()
				itemView.cloudSubText.visibility = View.GONE
			} else {
				itemView.cloudSubText.visibility = View.VISIBLE
				itemView.cloudText.text = cloudModel.location()
				itemView.cloudSubText.text = cloudModel.storage()
			}
		}
	}
}
