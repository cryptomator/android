package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AlertDialog
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.DialogPcloudCredentialsUpdatedBinding
import org.cryptomator.presentation.util.ResourceHelper

@Dialog
class PCloudCredentialsUpdatedDialog : BaseDialog<PCloudCredentialsUpdatedDialog.Callback, DialogPcloudCredentialsUpdatedBinding>(DialogPcloudCredentialsUpdatedBinding::inflate) {

	interface Callback {

		fun onNotifyForPCloudCredentialsUpdateFinished()
	}

	private val someActivityResultLauncher = registerForActivityResult(StartActivityForResult()) {
		dismiss()
		callback?.onNotifyForPCloudCredentialsUpdateFinished()
	}

	override fun onStart() {
		super.onStart()
		val dialog = dialog as AlertDialog?
		dialog?.let {
			binding.tvPcloudCredentialsUpdated.setOnClickListener {
				someActivityResultLauncher.launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.pcloud.com")))
			}
		}
	}

	public override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		val username = requireArguments().getString(ARG_PCLOUD_USERNAME)
		builder //
			.setTitle(String.format(ResourceHelper.getString(R.string.dialog_pcloud_credentials_updated_title), username)) //
			.setNeutralButton(getString(R.string.dialog_unable_to_share_positive_button)) { _: DialogInterface, _: Int ->
				callback?.onNotifyForPCloudCredentialsUpdateFinished()
			}
		return builder.create()
	}

	public override fun setupView() {
		// empty
	}

	companion object {

		private const val ARG_PCLOUD_USERNAME = "USERNAME"
		fun newInstance(username: String): PCloudCredentialsUpdatedDialog {
			val args = Bundle()
			args.putString(ARG_PCLOUD_USERNAME, username)
			val fragment = PCloudCredentialsUpdatedDialog()
			fragment.arguments = args
			return fragment
		}

	}
}
