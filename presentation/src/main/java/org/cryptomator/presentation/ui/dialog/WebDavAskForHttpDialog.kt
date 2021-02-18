package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import java.net.URI
import kotlinx.android.synthetic.main.dialog_ask_for_http.cb_select_http

@Dialog(R.layout.dialog_ask_for_http)
class WebDavAskForHttpDialog : BaseDialog<WebDavAskForHttpDialog.Callback>() {

	private lateinit var uri: URI
	private lateinit var username: String
	private lateinit var password: String

	private var certificate: String? = null
	private var cloudId: Long? = null

	interface Callback {

		fun onAksForHttpFinished(username: String, password: String, url: String, cloudId: Long?, certificate: String?)
	}

	public override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		uri = requireArguments().getSerializable(URI_ARG) as URI
		username = requireArguments().getString(USERNAME_ARG) as String
		password = requireArguments().getString(PASSWORD_ARG) as String
		cloudId = requireArguments().getString(CLOUD_ID_ARG)?.let { java.lang.Long.getLong(it) }
		certificate = requireArguments().getString(CERTIFICATE_ARG)
		builder //
				.setTitle(R.string.dialog_http_security_title) //
				.setNeutralButton(getString(R.string.dialog_unable_to_share_positive_button)
				) { _: DialogInterface, _: Int -> callback?.onAksForHttpFinished(username, password, uriWithDesiredProtocol(cb_select_http.isChecked), cloudId, certificate) }
		return builder.create()
	}

	public override fun setupView() {
		// empty
	}

	private fun uriWithDesiredProtocol(switchToHttps: Boolean): String {
		return if (switchToHttps && uri.scheme != "https") {
			uriSwitchedToHttps()
		} else {
			uri.toString()
		}
	}

	private fun uriSwitchedToHttps(): String {
		val newUri = StringBuilder("https://")
		newUri.append(uri.host)
		appendPort(newUri)
		newUri.append(uri.rawPath)
		return newUri.toString()
	}

	private fun appendPort(newUri: StringBuilder) {
		if (uri.port != -1 && uri.port != 80) {
			newUri.append(':').append(uri.port)
		}
	}

	companion object {

		private const val URI_ARG = "uri"
		private const val USERNAME_ARG = "username"
		private const val PASSWORD_ARG = "password"
		private const val CLOUD_ID_ARG = "cloudId"
		private const val CERTIFICATE_ARG = "certificate"

		fun newInstance(uri: URI, username: String, password: String, cloudId: Long?, certificate: String?): DialogFragment {
			val dialog = WebDavAskForHttpDialog()
			val args = Bundle()
			args.putSerializable(URI_ARG, uri)
			args.putSerializable(USERNAME_ARG, username)
			args.putSerializable(PASSWORD_ARG, password)
			args.putSerializable(CLOUD_ID_ARG, "" + cloudId)
			args.putString(CERTIFICATE_ARG, certificate)
			dialog.arguments = args
			return dialog
		}
	}
}
