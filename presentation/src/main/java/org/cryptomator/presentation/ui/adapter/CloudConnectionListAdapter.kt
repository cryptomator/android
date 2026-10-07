package org.cryptomator.presentation.ui.adapter

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.presentation.databinding.ItemBrowseCloudModelConnectionsBinding
import org.cryptomator.presentation.model.CloudModel
import org.cryptomator.presentation.model.LocalStorageModel
import org.cryptomator.presentation.model.OnedriveCloudModel
import org.cryptomator.presentation.model.PCloudModel
import org.cryptomator.presentation.model.S3CloudModel
import org.cryptomator.presentation.model.WebDavCloudModel
import org.cryptomator.presentation.model.comparator.CloudModelComparator
import org.cryptomator.presentation.ui.adapter.CloudConnectionListAdapter.CloudConnectionHolder
import java.net.URISyntaxException
import javax.inject.Inject

class CloudConnectionListAdapter @Inject
internal constructor(context: Context) : RecyclerViewBaseAdapter<CloudModel, CloudConnectionListAdapter.Callback, CloudConnectionHolder, ItemBrowseCloudModelConnectionsBinding>(CloudModelComparator(context)) {

	interface Callback {

		fun onCloudConnectionClicked(cloudModel: CloudModel)

		fun onCloudSettingsClicked(cloudModel: CloudModel)
	}

	override fun getItemBinding(inflater: LayoutInflater, parent: ViewGroup?, viewType: Int): ItemBrowseCloudModelConnectionsBinding {
		return ItemBrowseCloudModelConnectionsBinding.inflate(inflater, parent, false)
	}

	override fun createViewHolder(binding: ItemBrowseCloudModelConnectionsBinding, viewType: Int): CloudConnectionHolder {
		return CloudConnectionHolder(binding)
	}

	fun setOnItemClickListener(callback: Callback) {
		this.callback = callback
	}

	inner class CloudConnectionHolder(private val binding: ItemBrowseCloudModelConnectionsBinding) : RecyclerViewBaseAdapter<CloudModel, CloudConnectionListAdapter.Callback, CloudConnectionHolder, ItemBrowseCloudModelConnectionsBinding>.ItemViewHolder(binding.root) {

		override fun bind(position: Int) {
			internalBind(getItem(position))
		}

		private fun internalBind(cloudModel: CloudModel) {
			binding.settings.setOnClickListener { callback.onCloudSettingsClicked(cloudModel) }

			binding.cloudImage.setImageResource(cloudModel.cloudType().cloudImageResource)

			itemView.setOnClickListener { callback.onCloudConnectionClicked(cloudModel) }

			when (cloudModel) {
				is OnedriveCloudModel -> {
					bindOnedriveCloudModel(cloudModel)
				}
				is WebDavCloudModel -> {
					bindWebDavCloudModel(cloudModel)
				}
				is PCloudModel -> {
					bindPCloudModel(cloudModel)
				}
				is S3CloudModel -> {
					bindS3loudModel(cloudModel)
				}
				is LocalStorageModel -> {
					bindLocalStorageCloudModel(cloudModel)
				}
			}
		}


		private fun bindOnedriveCloudModel(cloudModel: OnedriveCloudModel) {
			binding.llCloudConnectionContent.cloudText.text = cloudModel.username()
			binding.llCloudConnectionContent.cloudSubText.visibility = View.GONE
		}

		private fun bindWebDavCloudModel(cloudModel: WebDavCloudModel) {
			try {
				val uri = Uri.parse(cloudModel.url())
				binding.llCloudConnectionContent.cloudText.text = uri.authority
				binding.llCloudConnectionContent.cloudSubText.text = String.format("%s • %s", cloudModel.username(), uri.path)
			} catch (e: URISyntaxException) {
				throw FatalBackendException("path in WebDAV cloud isn't correct (no uri)")
			}
		}

		private fun bindPCloudModel(cloudModel: PCloudModel) {
			binding.llCloudConnectionContent.cloudText.text = cloudModel.username()
			binding.llCloudConnectionContent.cloudSubText.visibility = View.GONE
		}


		private fun bindS3loudModel(cloudModel: S3CloudModel) {
			binding.llCloudConnectionContent.cloudText.text = cloudModel.username()
			binding.llCloudConnectionContent.cloudSubText.visibility = View.GONE
		}

		private fun bindLocalStorageCloudModel(cloudModel: LocalStorageModel) {
			if (cloudModel.location().isEmpty()) {
				binding.llCloudConnectionContent.cloudText.text = cloudModel.storage()
				binding.llCloudConnectionContent.cloudSubText.visibility = View.GONE
			} else {
				binding.llCloudConnectionContent.cloudSubText.visibility = View.VISIBLE
				binding.llCloudConnectionContent.cloudText.text = cloudModel.location()
				binding.llCloudConnectionContent.cloudSubText.text = cloudModel.storage()
			}
		}
	}
}
