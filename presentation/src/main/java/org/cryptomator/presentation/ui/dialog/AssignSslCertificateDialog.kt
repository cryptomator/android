package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import org.cryptomator.data.util.X509CertificateHelper
import org.cryptomator.domain.WebDavCloud
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import kotlinx.android.synthetic.main.dialog_handle_ssl_certificate.cb_accept_certificate
import kotlinx.android.synthetic.main.dialog_handle_ssl_certificate.certificate_details
import kotlinx.android.synthetic.main.dialog_handle_ssl_certificate.show_certificate
import kotlinx.android.synthetic.main.dialog_handle_ssl_certificate.tv_finger_print_text

@Dialog(R.layout.dialog_handle_ssl_certificate)
class AssignSslCertificateDialog : BaseDialog<AssignSslCertificateDialog.Callback>() {

	private lateinit var certificate: X509Certificate

	interface Callback {

		fun onAcceptCertificateClicked(cloud: WebDavCloud, certificate: X509Certificate)
		fun onAcceptCertificateDenied()
	}

	public override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		builder //
			.setTitle(requireContext().getString(R.string.dialog_accept_ssl_certificate_title))
			.setPositiveButton(requireActivity().getString(R.string.dialog_unable_to_share_positive_button)) { _: DialogInterface, _: Int ->
				val cloud = requireArguments().getSerializable(WEBDAV_CLOUD) as WebDavCloud
				callback?.onAcceptCertificateClicked(cloud, certificate)
			} //
			.setNegativeButton(requireContext().getString(R.string.dialog_button_cancel)) { _: DialogInterface, _: Int ->
				callback?.onAcceptCertificateDenied()
			}
		return builder.create()
	}

	public override fun setupView() {
		certificate = requireArguments().getSerializable(CERTIFICATE) as X509Certificate
		try {
			tv_finger_print_text.text = X509CertificateHelper.getFingerprintFormatted(certificate)
			certificate_details.text = certificate.toString()
		} catch (e: CertificateException) {
			throw FatalBackendException(e)
		}

		show_certificate.setOnClickListener {
			run {
				certificate_details.visibility = View.VISIBLE
				show_certificate.visibility = View.GONE
			}
		}

		cb_accept_certificate.setOnCheckedChangeListener { _, isChecked ->
			run {
				alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = isChecked
			}
		}
	}

	override fun onStart() {
		super.onStart()
		alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = false
	}

	private val alertDialog: AlertDialog
		get() = dialog as AlertDialog

	companion object {

		private const val CERTIFICATE = "certificate"
		private const val WEBDAV_CLOUD = "webdavcloud"
		fun newInstance(cloud: WebDavCloud, certificate: X509Certificate): AssignSslCertificateDialog {
			val dialog = AssignSslCertificateDialog()
			val args = Bundle()
			args.putSerializable(WEBDAV_CLOUD, cloud)
			args.putSerializable(CERTIFICATE, certificate)
			dialog.arguments = args
			return dialog
		}
	}
}
