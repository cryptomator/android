package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.DialogNoDirFileOrEmptyBinding

@Dialog
class NoDirFileOrEmptyDialog : BaseDialog<NoDirFileOrEmptyDialog.CallBack, DialogNoDirFileOrEmptyBinding>(DialogNoDirFileOrEmptyBinding::inflate) {

	interface CallBack {

		fun navigateFolderBackBecauseNoDirFile()
	}

	public override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		builder //
			.setTitle(R.string.dialog_no_dir_file_title) //
			.setNeutralButton(R.string.dialog_no_dir_file_back_button) { dialog: DialogInterface, _: Int ->
				callback?.navigateFolderBackBecauseNoDirFile()
				dialog.dismiss()
			}
		return builder.create()
	}

	override fun disableDialogWhenObscured(): Boolean {
		return false
	}

	public override fun setupView() {
		val cryptoFolderName = requireArguments().getSerializable(ARG_CRYPTO_FOLDER_NAME) as String
		val cloudFolderPath = requireArguments().getSerializable(ARG_CLOUD_FOLDER_PATH) as String
		binding.tvNoDirFileOrEmptyInfo.text = String.format(getString(R.string.dialog_no_dir_file_message), cryptoFolderName, cloudFolderPath)
	}

	companion object {

		private const val ARG_CRYPTO_FOLDER_NAME = "argCryptoFolderName"
		private const val ARG_CLOUD_FOLDER_PATH = "argCloudFolderPath"
		fun newInstance(cryptoFolderName: String, cloudFolderPath: String): DialogFragment {
			val noDirFileOrEmptyDialog = NoDirFileOrEmptyDialog()
			val args = Bundle()
			args.putSerializable(ARG_CRYPTO_FOLDER_NAME, cryptoFolderName)
			args.putSerializable(ARG_CLOUD_FOLDER_PATH, cloudFolderPath)
			noDirFileOrEmptyDialog.arguments = args
			return noDirFileOrEmptyDialog
		}
	}
}
