package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import android.text.method.LinkMovementMethod
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import kotlinx.android.synthetic.main.dialog_sym_link.tv_sym_link_info

@Dialog(R.layout.dialog_sym_link)
class SymLinkDialog : BaseDialog<SymLinkDialog.CallBack?>() {

	interface CallBack {

		fun navigateFolderBackBecauseSymlink()
	}

	public override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		builder //
				.setTitle(R.string.dialog_sym_link_title) //
				.setNeutralButton(R.string.dialog_sym_link_back_button) { dialog: DialogInterface, _: Int ->
					callback?.navigateFolderBackBecauseSymlink()
					dialog.dismiss()
				}
		return builder.create()
	}

	public override fun setupView() {
		tv_sym_link_info.movementMethod = LinkMovementMethod.getInstance()
	}

	companion object {

		fun newInstance(): DialogFragment {
			return SymLinkDialog()
		}
	}
}
