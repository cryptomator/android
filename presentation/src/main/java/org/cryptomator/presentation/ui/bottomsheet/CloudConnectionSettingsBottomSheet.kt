package org.cryptomator.presentation.ui.bottomsheet

import android.os.Bundle
import android.view.View
import org.cryptomator.generator.BottomSheet
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.DialogBottomSheetCloudSettingsBinding
import org.cryptomator.presentation.model.CloudModel
import org.cryptomator.presentation.model.CloudTypeModel
import org.cryptomator.presentation.model.LocalStorageModel
import org.cryptomator.presentation.model.OnedriveCloudModel
import org.cryptomator.presentation.model.PCloudModel
import org.cryptomator.presentation.model.S3CloudModel
import org.cryptomator.presentation.model.WebDavCloudModel

@BottomSheet(R.layout.dialog_bottom_sheet_cloud_settings)
class CloudConnectionSettingsBottomSheet : BaseBottomSheet<CloudConnectionSettingsBottomSheet.Callback, DialogBottomSheetCloudSettingsBinding>(DialogBottomSheetCloudSettingsBinding::inflate) {

	interface Callback {

		fun onChangeCloudClicked(cloudModel: CloudModel)
		fun onDeleteCloudClicked(cloudModel: CloudModel)
	}

	override fun setupView() {
		val cloudModel = requireArguments().getSerializable(CLOUD_NODE_ARG) as CloudModel

		when (cloudModel.cloudType()) {
			CloudTypeModel.ONEDRIVE -> bindViewForOnedrive(cloudModel as OnedriveCloudModel)
			CloudTypeModel.WEBDAV -> bindViewForWebDAV(cloudModel as WebDavCloudModel)
			CloudTypeModel.PCLOUD -> bindViewForPCloud(cloudModel as PCloudModel)
			CloudTypeModel.S3 -> bindViewForS3(cloudModel as S3CloudModel)
			CloudTypeModel.LOCAL -> bindViewForLocal(cloudModel as LocalStorageModel)
			else -> throw IllegalStateException("Cloud model is not binded in the view")
		}

		binding.ivCloudImage.setImageResource(cloudModel.cloudType().cloudImageResource)
		binding.changeCloud.setOnClickListener {
			callback?.onChangeCloudClicked(cloudModel)
			dismiss()
		}
		binding.deleteCloud.setOnClickListener {
			callback?.onDeleteCloudClicked(cloudModel)
			dismiss()
		}
	}

	private fun bindViewForLocal(cloudModel: LocalStorageModel) {
		if (cloudModel.location().isEmpty()) {
			binding.tvCloudName.text = cloudModel.storage()
			binding.tvCloudSubtext.visibility = View.GONE
		} else {
			binding.tvCloudName.text = cloudModel.location()
			binding.tvCloudSubtext.text = cloudModel.storage()
		}
	}

	private fun bindViewForOnedrive(cloudModel: OnedriveCloudModel) {
		binding.changeCloud.visibility = View.GONE
		binding.tvCloudSubtext.text = cloudModel.username()
	}

	private fun bindViewForWebDAV(cloudModel: WebDavCloudModel) {
		binding.changeCloud.visibility = View.VISIBLE
		binding.tvCloudName.text = cloudModel.url()
		binding.tvCloudSubtext.text = cloudModel.username()
	}

	private fun bindViewForPCloud(cloudModel: PCloudModel) {
		binding.changeCloud.visibility = View.GONE
		binding.tvCloudName.text = cloudModel.username()
	}

	private fun bindViewForS3(cloudModel: S3CloudModel) {
		binding.changeCloud.visibility = View.VISIBLE
		binding.tvCloudName.text = cloudModel.username()
	}

	companion object {

		private const val CLOUD_NODE_ARG = "cloudModel"
		fun newInstance(cloudModel: CloudModel): CloudConnectionSettingsBottomSheet {
			val dialog = CloudConnectionSettingsBottomSheet()
			val args = Bundle()
			args.putSerializable(CLOUD_NODE_ARG, cloudModel)
			dialog.arguments = args
			return dialog
		}
	}
}
