package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import kotlinx.android.synthetic.main.dialog_no_dir_file.tv_no_dir_file_info

@Dialog(R.layout.dialog_no_dir_file)
class NoDirFileDialog : BaseDialog<NoDirFileDialog.CallBack>() {

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
		tv_no_dir_file_info.text = String.format(getString(R.string.dialog_no_dir_file_message), cryptoFolderName, cloudFolderPath)
	}

	companion object {

		private const val ARG_CRYPTO_FOLDER_NAME = "argCryptoFolderName"
		private const val ARG_CLOUD_FOLDER_PATH = "argCloudFolderPath"
		fun newInstance(cryptoFolderName: String, cloudFolderPath: String): DialogFragment {
			val noDirFileDialog = NoDirFileDialog()
			val args = Bundle()
			args.putSerializable(ARG_CRYPTO_FOLDER_NAME, cryptoFolderName)
			args.putSerializable(ARG_CLOUD_FOLDER_PATH, cloudFolderPath)
			noDirFileDialog.arguments = args
			return noDirFileDialog
		}
	}
}
