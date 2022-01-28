package org.cryptomator.presentation.ui.bottomsheet

import android.os.Bundle
import android.view.View
import org.cryptomator.generator.BottomSheet
import org.cryptomator.presentation.R
import org.cryptomator.presentation.model.CloudModel
import org.cryptomator.presentation.model.CloudTypeModel
import org.cryptomator.presentation.model.LocalStorageModel
import org.cryptomator.presentation.model.OnedriveCloudModel
import org.cryptomator.presentation.model.PCloudModel
import org.cryptomator.presentation.model.S3CloudModel
import org.cryptomator.presentation.model.WebDavCloudModel
import kotlinx.android.synthetic.main.dialog_bottom_sheet_cloud_settings.change_cloud
import kotlinx.android.synthetic.main.dialog_bottom_sheet_cloud_settings.delete_cloud
import kotlinx.android.synthetic.main.dialog_bottom_sheet_cloud_settings.iv_cloud_image
import kotlinx.android.synthetic.main.dialog_bottom_sheet_cloud_settings.tv_cloud_name
import kotlinx.android.synthetic.main.dialog_bottom_sheet_cloud_settings.tv_cloud_subtext

@BottomSheet(R.layout.dialog_bottom_sheet_cloud_settings)
class CloudConnectionSettingsBottomSheet : BaseBottomSheet<CloudConnectionSettingsBottomSheet.Callback>() {

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

		iv_cloud_image.setImageResource(cloudModel.cloudType().cloudImageResource)
		change_cloud.setOnClickListener {
			callback?.onChangeCloudClicked(cloudModel)
			dismiss()
		}
		delete_cloud.setOnClickListener {
			callback?.onDeleteCloudClicked(cloudModel)
			dismiss()
		}
	}

	private fun bindViewForLocal(cloudModel: LocalStorageModel) {
		if (cloudModel.location().isEmpty()) {
			tv_cloud_name.text = cloudModel.storage()
			tv_cloud_subtext.visibility = View.GONE
		} else {
			tv_cloud_name.text = cloudModel.location()
			tv_cloud_subtext.text = cloudModel.storage()
		}
	}

	private fun bindViewForOnedrive(cloudModel: OnedriveCloudModel) {
		change_cloud.visibility = View.GONE
		tv_cloud_subtext.text = cloudModel.username()
	}

	private fun bindViewForWebDAV(cloudModel: WebDavCloudModel) {
		change_cloud.visibility = View.VISIBLE
		tv_cloud_name.text = cloudModel.url()
		tv_cloud_subtext.text = cloudModel.username()
	}

	private fun bindViewForPCloud(cloudModel: PCloudModel) {
		change_cloud.visibility = View.GONE
		tv_cloud_name.text = cloudModel.username()
	}

	private fun bindViewForS3(cloudModel: S3CloudModel) {
		change_cloud.visibility = View.VISIBLE
		tv_cloud_name.text = cloudModel.username()
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
