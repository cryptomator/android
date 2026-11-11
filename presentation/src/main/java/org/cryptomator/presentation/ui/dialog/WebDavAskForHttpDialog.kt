package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.DialogAskForHttpBinding
import org.cryptomator.presentation.util.WebDavUrl

@Dialog
class WebDavAskForHttpDialog : BaseDialog<WebDavAskForHttpDialog.Callback, DialogAskForHttpBinding>(DialogAskForHttpBinding::inflate) {

	private lateinit var url: String
	private lateinit var username: String
	private lateinit var password: String

	private var certificate: String? = null
	private var cloudId: Long? = null

	interface Callback {

		fun onAksForHttpFinished(username: String, password: String, url: String, cloudId: Long?, certificate: String?)
	}

	public override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		url = requireArguments().getString(URL_ARG) as String
		username = requireArguments().getString(USERNAME_ARG) as String
		password = requireArguments().getString(PASSWORD_ARG) as String
		cloudId = requireArguments().getString(CLOUD_ID_ARG)?.let { java.lang.Long.getLong(it) }
		certificate = requireArguments().getString(CERTIFICATE_ARG)
		builder //
			.setTitle(R.string.dialog_http_security_title) //
			.setNeutralButton(
				getString(R.string.dialog_unable_to_share_positive_button)
			) { _: DialogInterface, _: Int -> callback?.onAksForHttpFinished(username, password, urlWithDesiredProtocol(binding.cbSelectHttp.isChecked), cloudId, certificate) }
		return builder.create()
	}

	public override fun setupView() {
		// empty
	}

	private fun urlWithDesiredProtocol(switchToHttps: Boolean): String {
		return if (switchToHttps) WebDavUrl.switchedToHttps(url) else url
	}

	companion object {

		private const val URL_ARG = "url"
		private const val USERNAME_ARG = "username"
		private const val PASSWORD_ARG = "password"
		private const val CLOUD_ID_ARG = "cloudId"
		private const val CERTIFICATE_ARG = "certificate"

		fun newInstance(url: String, username: String, password: String, cloudId: Long?, certificate: String?): DialogFragment {
			val dialog = WebDavAskForHttpDialog()
			val args = Bundle()
			args.putString(URL_ARG, url)
			args.putSerializable(USERNAME_ARG, username)
			args.putSerializable(PASSWORD_ARG, password)
			args.putSerializable(CLOUD_ID_ARG, "" + cloudId)
			args.putString(CERTIFICATE_ARG, certificate)
			dialog.arguments = args
			return dialog
		}
	}
}
