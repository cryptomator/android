package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AlertDialog
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.util.ResourceHelper
import kotlinx.android.synthetic.main.dialog_pcloud_credentials_updated.tv_pcloud_credentials_updated


@Dialog(R.layout.dialog_pcloud_credentials_updated)
class PCloudCredentialsUpdatedDialog : BaseDialog<PCloudCredentialsUpdatedDialog.Callback>() {

	interface Callback {

		fun onNotifyForPCloudCredentialsUpdateFinished()
	}

	val someActivityResultLauncher = registerForActivityResult(StartActivityForResult()) {
		dismiss()
		callback?.onNotifyForPCloudCredentialsUpdateFinished()
	}

	override fun onStart() {
		super.onStart()
		val dialog = dialog as AlertDialog?
		dialog?.let {
			tv_pcloud_credentials_updated.setOnClickListener {
				someActivityResultLauncher.launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.pcloud.com")))
			}
		}
	}

	public override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		val username = requireArguments().getString(ARG_PCLOUD_USERNAME)
		builder //
			.setTitle(String.format(ResourceHelper.getString(R.string.dialog_pcloud_credentials_updated_title), username)) //
			.setNeutralButton(getString(R.string.dialog_pcloud_credentials_updated_neutral_button)) { _: DialogInterface, _: Int ->
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
