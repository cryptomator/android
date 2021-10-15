package org.cryptomator.presentation.ui.fragment

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import org.cryptomator.generator.Fragment
import org.cryptomator.presentation.R
import org.cryptomator.presentation.model.WebDavCloudModel
import org.cryptomator.presentation.presenter.WebDavAddOrChangePresenter
import org.cryptomator.util.crypto.CredentialCryptor
import org.cryptomator.util.crypto.FatalCryptoException
import javax.inject.Inject
import kotlinx.android.synthetic.main.fragment_setup_webdav.createCloudButton
import kotlinx.android.synthetic.main.fragment_setup_webdav.passwordEditText
import kotlinx.android.synthetic.main.fragment_setup_webdav.urlPortEditText
import kotlinx.android.synthetic.main.fragment_setup_webdav.userNameEditText
import timber.log.Timber

@Fragment(R.layout.fragment_setup_webdav)
class WebDavAddOrChangeFragment : BaseFragment() {

	@Inject
	lateinit var webDavAddOrChangePresenter: WebDavAddOrChangePresenter

	private var cloudId: Long? = null
	private var certificate: String? = null

	private val webDavCloudModel: WebDavCloudModel?
		get() = arguments?.getSerializable(ARG_WEBDAV_CLOUD) as? WebDavCloudModel

	override fun setupView() {
		createCloudButton.setOnClickListener { createCloud() }
		createCloudButton.setOnEditorActionListener { _, actionId, _ ->
			if (actionId == EditorInfo.IME_ACTION_DONE) {
				createCloud()
			}
			false
		}

		urlPortEditText.text?.length?.let { urlPortEditText.setSelection(it) }
		showEditableCloudContent(webDavCloudModel)
	}

	private fun showEditableCloudContent(webDavCloudModel: WebDavCloudModel?) {
		if (webDavCloudModel != null) {
			urlPortEditText.setText(webDavCloudModel.url())
			userNameEditText.setText(webDavCloudModel.username())
			passwordEditText.setText(getPassword(webDavCloudModel.accessToken()))
			cloudId = webDavCloudModel.id()
			certificate = webDavCloudModel.certificate()
		}
	}

	private fun getPassword(password: String?): String {
		return if (password != null) {
			try {
				CredentialCryptor //
					.getInstance(activity?.applicationContext) //
					.decrypt(password)
			} catch (e: FatalCryptoException) {
				Timber.tag("WebdavAddOrCangeFragmnt").e(e, "Unable to decrypt password, clearing it")
				""
			}
		} else ""
	}

	private fun createCloud() {
		val urlPort = urlPortEditText.text.toString().trim()
		val username = userNameEditText.text.toString().trim()
		val password = passwordEditText.text.toString().trim()

		webDavAddOrChangePresenter.checkUserInput(urlPort, username, password, cloudId, certificate)
	}

	fun hideKeyboard() {
		hideKeyboard(passwordEditText)
	}

	companion object {

		private const val ARG_WEBDAV_CLOUD = "WEBDAV_CLOUD"

		fun newInstance(cloudModel: WebDavCloudModel?): WebDavAddOrChangeFragment {
			val result = WebDavAddOrChangeFragment()
			val args = Bundle()
			args.putSerializable(ARG_WEBDAV_CLOUD, cloudModel)
			result.arguments = args
			return result
		}
	}

}
